package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.AccessTokens
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime

/**
 * Persists long-lived personal access tokens (`qm_pat_…`) that let an agent or CLI call
 * the API without holding a user's password. Only a sha256 hash is ever stored — the
 * plaintext token is returned once, at creation, and is unrecoverable afterwards.
 */
class AccessTokenRepository(private val database: Database) {

    data class CreatedToken(
        val id: Long,
        val token: String,
        val last4: String,
        val name: String,
        val scopes: Set<String>,
        val createdAt: LocalDateTime,
        val expiresAt: LocalDateTime?,
    )

    data class ValidatedToken(val userId: Long, val scopes: Set<String>)

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun randomHex(byteLen: Int): String {
        val bytes = ByteArray(byteLen)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encodeScopes(scopes: Set<String>): String = scopes.joinToString(",")

    private fun decodeScopes(raw: String): Set<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

    /** Mints a fresh token for [userId]. Returns the plaintext token — shown once. */
    fun create(
        userId: Long,
        name: String,
        scopes: Set<String>,
        expiresInDays: Long? = null,
    ): CreatedToken {
        val token = "qm_pat_${randomHex(32)}"
        val tokenSha256 = sha256(token)
        val createdAt = LocalDateTime.now()
        val expiresAt = expiresInDays?.let { createdAt.plusDays(it) }

        val id = transaction(database) {
            AccessTokens.insert {
                it[this.userId] = userId
                it[this.name] = name
                it[this.tokenSha256] = tokenSha256
                it[this.tokenLast4] = token.takeLast(4)
                it[this.scopes] = encodeScopes(scopes)
                it[this.createdAt] = createdAt
                it[this.expiresAt] = expiresAt
            } get AccessTokens.id
        }

        return CreatedToken(id, token, token.takeLast(4), name, scopes, createdAt, expiresAt)
    }

    /**
     * Resolves a plaintext token to its owning user and scopes, or null if the token is
     * unknown, revoked, or expired. Advances `last_used_at` on success.
     */
    fun validate(token: String): ValidatedToken? {
        val tokenSha256 = sha256(token)
        val now = LocalDateTime.now()
        return transaction(database) {
            val row = AccessTokens.selectAll()
                .where {
                    (AccessTokens.tokenSha256 eq tokenSha256) and
                    (AccessTokens.revokedAt.isNull())
                }
                .limit(1)
                .singleOrNull() ?: return@transaction null

            val expiresAt = row[AccessTokens.expiresAt]
            if (expiresAt != null && expiresAt.isBefore(now)) return@transaction null

            AccessTokens.update({ AccessTokens.id eq row[AccessTokens.id] }) {
                it[lastUsedAt] = now
            }

            ValidatedToken(row[AccessTokens.userId], decodeScopes(row[AccessTokens.scopes]))
        }
    }

    /** Revokes a token. Returns false if it does not exist or is not owned by [userId]. */
    fun revoke(id: Long, userId: Long): Boolean {
        return transaction(database) {
            AccessTokens.update(
                where = {
                    (AccessTokens.id eq id) and
                    (AccessTokens.userId eq userId) and
                    (AccessTokens.revokedAt.isNull())
                },
                body = { it[revokedAt] = LocalDateTime.now() },
            ) > 0
        }
    }

    /** Non-revoked tokens owned by [userId], newest first. Never includes plaintext. */
    fun list(userId: Long): List<Map<String, Any?>> {
        return transaction(database) {
            AccessTokens.selectAll()
                .where { (AccessTokens.userId eq userId) and (AccessTokens.revokedAt.isNull()) }
                .orderBy(AccessTokens.createdAt to org.jetbrains.exposed.v1.core.SortOrder.DESC)
                .map { rowToMap(it) }
        }
    }

    private fun rowToMap(row: ResultRow) = mapOf(
        "id" to row[AccessTokens.id],
        "name" to row[AccessTokens.name],
        "last4" to row[AccessTokens.tokenLast4],
        "scopes" to decodeScopes(row[AccessTokens.scopes]),
        "createdAt" to row[AccessTokens.createdAt],
        "expiresAt" to row[AccessTokens.expiresAt],
        "lastUsedAt" to row[AccessTokens.lastUsedAt],
    )
}
