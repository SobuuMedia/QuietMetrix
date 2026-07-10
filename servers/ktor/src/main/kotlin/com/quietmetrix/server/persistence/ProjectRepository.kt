package com.quietmetrix.server.persistence

import at.favre.lib.crypto.bcrypt.BCrypt
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

class ProjectRepository(private val database: Database) {

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun randomHex(byteLen: Int): String {
        val bytes = ByteArray(byteLen)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun create(name: String, description: String?, ownerUserId: Long): String {
        val apiKey = "qm_ak_${UUID.randomUUID().toString().replace("-", "")}"
        val apiKeyHash = BCrypt.withDefaults().hashToString(12, apiKey.toCharArray())
        val apiKeySha256 = sha256(apiKey)

        transaction(database) {
            Projects.insert {
                it[this.name] = name
                it[this.description] = description
                it[this.ownerUserId] = ownerUserId
                it[this.apiKeyHash] = apiKeyHash
                it[this.apiKeySha256] = apiKeySha256
                it[this.apiKeyLast4] = apiKey.takeLast(4)
                it[this.installSalt] = randomHex(32)
            }
        }
        return apiKey
    }

    /**
     * Issues a fresh API key for the project, invalidating the old one. Returns
     * the new plaintext key (shown once) or null if the project does not exist.
     *
     * Also rotates the per-project install salt and discards existing `install_meta`
     * rows: their salted hashes cannot be recomputed against the new salt, and rotation
     * is an abuse-response action. See docs/security/publishable-api-key.md.
     */
    fun regenerateApiKey(id: Long): String? {
        val apiKey = "qm_ak_${UUID.randomUUID().toString().replace("-", "")}"
        val apiKeyHash = BCrypt.withDefaults().hashToString(12, apiKey.toCharArray())
        val apiKeySha256 = sha256(apiKey)
        val newSalt = randomHex(32)
        val updated = transaction(database) {
            val n = Projects.update(
                where = { (Projects.id eq id) and (Projects.deletedAt.isNull()) },
                body = {
                    it[this.apiKeyHash] = apiKeyHash
                    it[this.apiKeySha256] = apiKeySha256
                    it[this.apiKeyLast4] = apiKey.takeLast(4)
                    it[this.installSalt] = newSalt
                }
            )
            if (n > 0) {
                com.quietmetrix.server.persistence.tables.InstallMeta
                    .deleteWhere { com.quietmetrix.server.persistence.tables.InstallMeta.projectId eq id }
            }
            n
        }
        return if (updated > 0) apiKey else null
    }

    /** Per-project install-id salt used to hash `anonymousId` before storage. */
    fun getInstallSalt(id: Long): String? {
        return transaction(database) {
            Projects.selectAll()
                .where { (Projects.id eq id) and (Projects.deletedAt.isNull()) }
                .limit(1)
                .map { it[Projects.installSalt] }
                .singleOrNull()
        }
    }

    data class StrictSchemaConfig(val enabled: Boolean, val allowedEvents: Set<String>)

    /** Stage 3 — per-project event-name allowlist. */
    fun getStrictSchema(id: Long): StrictSchemaConfig {
        return transaction(database) {
            Projects.selectAll()
                .where { (Projects.id eq id) and (Projects.deletedAt.isNull()) }
                .limit(1)
                .map {
                    StrictSchemaConfig(
                        enabled = it[Projects.strictSchema],
                        allowedEvents = parseAllowedEvents(it[Projects.allowedEvents]),
                    )
                }
                .singleOrNull()
        } ?: StrictSchemaConfig(false, emptySet())
    }

    fun setStrictSchema(id: Long, enabled: Boolean, allowedEvents: Set<String>): Boolean {
        val json = if (allowedEvents.isEmpty()) null else encodeAllowedEvents(allowedEvents)
        return transaction(database) {
            Projects.update(
                where = { (Projects.id eq id) and (Projects.deletedAt.isNull()) },
                body = {
                    it[this.strictSchema] = enabled
                    it[this.allowedEvents] = json
                },
            ) > 0
        }
    }

    private fun parseAllowedEvents(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return json.trim()
            .trim('[', ']')
            .split(',')
            .map { it.trim().trim('"') }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun encodeAllowedEvents(events: Set<String>): String =
        events.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }

    fun findById(id: Long): Map<String, Any?>? {
        return transaction(database) {
            Projects
                .selectAll()
                .where { (Projects.id eq id) and (Projects.deletedAt.isNull()) }
                .map { rowToMap(it) }
                .singleOrNull()
        }
    }

    fun findByOwnerId(ownerUserId: Long, limit: Int = 50, offset: Int = 0): List<Map<String, Any?>> {
        return transaction(database) {
            Projects
                .selectAll()
                .where { (Projects.ownerUserId eq ownerUserId) and (Projects.deletedAt.isNull()) }
                .limit(limit).offset(offset.toLong())
                .map { rowToMap(it) }
        }
    }

    /** All non-deleted projects (admin view). */
    fun findAll(limit: Int = 50, offset: Int = 0): List<Map<String, Any?>> {
        return transaction(database) {
            Projects
                .selectAll()
                .where { Projects.deletedAt.isNull() }
                .limit(limit).offset(offset.toLong())
                .map { rowToMap(it) }
        }
    }

    fun countAll(): Long {
        return transaction(database) {
            Projects.selectAll().where { Projects.deletedAt.isNull() }.count()
        }
    }

    fun countByOwnerId(ownerUserId: Long): Long {
        return transaction(database) {
            Projects
                .selectAll()
                .where { (Projects.ownerUserId eq ownerUserId) and (Projects.deletedAt.isNull()) }
                .count()
        }
    }

    fun findAccessibleByUserId(userId: Long, limit: Int = 50, offset: Int = 0): List<Map<String, Any?>> {
        return transaction(database) {
            (Projects leftJoin ProjectMembers)
                .selectAll()
                .where {
                    (Projects.deletedAt.isNull()) and
                    ((Projects.ownerUserId eq userId) or (ProjectMembers.userId eq userId))
                }
                .withDistinct()
                .limit(limit).offset(offset.toLong())
                .map { rowToMap(it) }
        }
    }

    fun countAccessibleByUserId(userId: Long): Long {
        return transaction(database) {
            (Projects leftJoin ProjectMembers)
                .selectAll()
                .where {
                    (Projects.deletedAt.isNull()) and
                    ((Projects.ownerUserId eq userId) or (ProjectMembers.userId eq userId))
                }
                .withDistinct()
                .count()
        }
    }

    fun updateName(id: Long, name: String): Boolean {
        return transaction(database) {
            Projects.update(
                where = { (Projects.id eq id) and (Projects.deletedAt.isNull()) },
                body = { it[this.name] = name }
            ) > 0
        }
    }

    fun softDelete(id: Long): Boolean {
        return transaction(database) {
            Projects.update(
                where = { (Projects.id eq id) and (Projects.deletedAt.isNull()) },
                body = { it[deletedAt] = LocalDateTime.now() }
            ) > 0
        }
    }

    fun validateApiKey(apiKey: String): Long? {
        val sha256Hash = sha256(apiKey)
        return transaction(database) {
            Projects.selectAll()
                .where { (Projects.apiKeySha256 eq sha256Hash) and (Projects.deletedAt.isNull()) }
                .limit(1)
                .map { it[Projects.id] }
                .singleOrNull()
        }
    }

    private fun rowToMap(row: ResultRow) = mapOf(
        "id" to row[Projects.id],
        "name" to row[Projects.name],
        "description" to row[Projects.description],
        "ownerUserId" to row[Projects.ownerUserId],
        "apiKeyHash" to row[Projects.apiKeyHash],
        "apiKeyLast4" to row[Projects.apiKeyLast4],
        "planId" to row[Projects.planId],
        "createdAt" to row[Projects.createdAt],
    )
}
