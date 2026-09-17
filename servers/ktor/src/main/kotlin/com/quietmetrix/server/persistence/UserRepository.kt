package com.quietmetrix.server.persistence

import at.favre.lib.crypto.bcrypt.BCrypt
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.security.MessageDigest
import java.time.LocalDateTime

class UserRepository(private val database: Database) {

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun create(email: String, password: String): Long {
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        return transaction(database) {
            (Users.insert {
                it[this.email] = email
                it[passwordHash] = hash
            } get Users.id)
        }
    }

    /** All users (admin view). */
    fun listAll(): List<Map<String, Any?>> = transaction(database) {
        Users.selectAll().map { rowToMap(it) }
    }

    fun countAdmins(): Long = transaction(database) {
        Users.selectAll().where { Users.role eq "admin" }.count()
    }

    /**
     * Creates an invited user (no usable password) with the given role and an
     * invite that expires in [expiryDays]. Returns the raw invite token, which is
     * only stored hashed.
     */
    fun createInvite(email: String, role: String, expiryDays: Long = 7): String {
        val rawToken = java.util.UUID.randomUUID().toString().replace("-", "") +
            java.util.UUID.randomUUID().toString().replace("-", "")
        transaction(database) {
            Users.insert {
                it[this.email] = email
                it[passwordHash] = "!invited"
                it[this.role] = role
                it[status] = "invited"
                it[inviteToken] = sha256(rawToken)
                it[inviteExpiresAt] = LocalDateTime.now().plusDays(expiryDays)
            }
        }
        return rawToken
    }

    /** Looks up a user by a still-valid (unexpired, invited) raw invite token. */
    fun findByInviteToken(rawToken: String): Map<String, Any?>? = transaction(database) {
        Users.selectAll()
            .where {
                (Users.inviteToken eq sha256(rawToken)) and
                (Users.status eq "invited") and
                (Users.inviteExpiresAt greater LocalDateTime.now())
            }
            .map { rowToMap(it) }
            .singleOrNull()
    }

    /** Sets the password, activates the account, and clears the invite. */
    fun acceptInvite(userId: Long, password: String): Boolean {
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        return transaction(database) {
            Users.update(where = { Users.id eq userId }, body = {
                it[passwordHash] = hash
                it[status] = "active"
                it[inviteToken] = null
                it[inviteExpiresAt] = null
            }) > 0
        }
    }

    fun updateRole(userId: Long, role: String): Boolean = transaction(database) {
        Users.update(where = { Users.id eq userId }, body = { it[this.role] = role }) > 0
    }

    fun delete(userId: Long): Boolean {
        return transaction(database) {
            Users.deleteWhere(limit = null) {
                Users.id eq userId
            } > 0
        }
    }

    fun findByEmail(email: String): Map<String, Any?>? {
        return transaction(database) {
            Users
                .selectAll()
                .where { Users.email eq email }
                .map { rowToMap(it) }
                .singleOrNull()
        }
    }

    fun findById(id: Long): Map<String, Any?>? {
        return transaction(database) {
            Users
                .selectAll()
                .where { Users.id eq id }
                .map { rowToMap(it) }
                .singleOrNull()
        }
    }

    fun validatePassword(email: String, password: String): Map<String, Any?>? {
        val user = findByEmail(email) ?: return null
        val hash = user["passwordHash"] as? String ?: return null
        val result = BCrypt.verifyer().verify(password.toCharArray(), hash)
        return if (result.verified) user else null
    }

    private fun rowToMap(row: ResultRow) = mapOf(
        "id" to row[Users.id],
        "email" to row[Users.email],
        "passwordHash" to row[Users.passwordHash],
        "planId" to row[Users.planId],
        "role" to row[Users.role],
        "status" to row[Users.status],
        "createdAt" to row[Users.createdAt],
    )
}