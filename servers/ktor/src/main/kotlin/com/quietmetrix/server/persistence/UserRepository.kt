package com.quietmetrix.server.persistence

import at.favre.lib.crypto.bcrypt.BCrypt
import com.quietmetrix.server.persistence.tables.AuthSessions
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.MessageDigest
import java.time.LocalDateTime

class AccountOwnsProjects : IllegalStateException("Delete this account's projects before deleting the account")

class UserRepository(private val database: Database) {
    fun allowAuthAttempt(key: String, limit: Int, windowSeconds: Long = 60): Boolean = com.quietmetrix.server.ratelimit.SharedRateLimiter(database).tryConsume(key, limit, windowSeconds)

    fun createSession(userId: Long, sessionId: String, refresh: String, expectedPasswordHash: String? = null): Boolean = transaction(database) {
        val user = Users.selectAll().where { Users.id eq userId }.forUpdate().singleOrNull() ?: return@transaction false
        if (user[Users.status] != "active" || (expectedPasswordHash != null && user[Users.passwordHash] != expectedPasswordHash)) return@transaction false
        AuthSessions.deleteWhere { expiresAt lessEq LocalDateTime.now() }
        AuthSessions.insert {
            it[id] = sessionId
            it[AuthSessions.userId] = userId
            it[refreshHash] = sha256(refresh)
            it[expiresAt] = LocalDateTime.now().plusDays(2)
        }
        true
    }

    fun rotateSession(userId: Long, sessionId: String, oldRefresh: String, newRefresh: String): Boolean = transaction(database) {
        AuthSessions.update({
            (AuthSessions.id eq sessionId) and (AuthSessions.userId eq userId) and
                (AuthSessions.refreshHash eq sha256(oldRefresh)) and (AuthSessions.expiresAt greater LocalDateTime.now())
        }) {
            it[refreshHash] = sha256(newRefresh)
            it[expiresAt] = LocalDateTime.now().plusDays(2)
        } == 1
    }

    fun sessionActive(userId: Long, sessionId: String): Boolean = transaction(database) {
        AuthSessions.selectAll().where {
            (AuthSessions.id eq sessionId) and (AuthSessions.userId eq userId) and (AuthSessions.expiresAt greater LocalDateTime.now())
        }.count() == 1L
    }

    fun revokeSession(sessionId: String, refresh: String): Boolean = transaction(database) {
        AuthSessions.deleteWhere { (id eq sessionId) and (refreshHash eq sha256(refresh)) } == 1
    }

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

    fun listTenant(tenantId: String): List<Map<String, Any?>> = transaction(database) {
        Users.selectAll().where { Users.tenantId eq tenantId }.map(::rowToMap)
    }

    fun markPlatformOperator(email: String) = transaction(database) {
        Users.update({ Users.email eq email }) { it[platformOperator] = true }
    }

    fun sameTenant(first: Long, second: Long): Boolean = findById(first)?.get("tenantId")?.let {
        it == findById(second)?.get("tenantId")
    } == true

    fun countAdmins(tenantId: String? = null): Long = transaction(database) {
        Users.selectAll().where { (Users.role eq "admin") and (if (tenantId == null) Op.TRUE else Users.tenantId eq tenantId) }.count()
    }

    /**
     * Creates an invited user (no usable password) with the given role and an
     * invite that expires in [expiryDays]. Returns the raw invite token, which is
     * only stored hashed.
     */
    fun createInvite(email: String, role: String, expiryDays: Long = 7, tenantId: String? = null): String {
        val rawToken = java.util.UUID.randomUUID().toString().replace("-", "") +
            java.util.UUID.randomUUID().toString().replace("-", "")
        transaction(database) {
            Users.insert {
                it[this.email] = email
                it[passwordHash] = "!invited"
                it[this.role] = role
                if (tenantId != null) it[this.tenantId] = tenantId
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
                (Users.status inList listOf("invited", "active")) and
                (Users.inviteExpiresAt greater LocalDateTime.now())
            }
            .map { rowToMap(it) }
            .singleOrNull()
    }

    /** Sets the password, activates the account, and clears the invite. */
    fun acceptInvite(userId: Long, password: String, rawToken: String? = null): Boolean {
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        return transaction(database) {
            val changed = Users.update(where = {
                (Users.id eq userId) and (if (rawToken != null) (Users.inviteToken eq sha256(rawToken)) and (Users.inviteExpiresAt greater LocalDateTime.now()) else Op.TRUE)
            }, body = {
                it[passwordHash] = hash
                it[status] = "active"
                it[inviteToken] = null
                it[inviteExpiresAt] = null
            }) > 0
            if (changed) AuthSessions.deleteWhere { AuthSessions.userId eq userId }
            changed
        }
    }

    fun createRecovery(userId: Long): String? = transaction(database) {
        val raw = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "")
        val changed = Users.update({ (Users.id eq userId) and (Users.status eq "active") }) {
            it[inviteToken] = sha256(raw)
            it[inviteExpiresAt] = LocalDateTime.now().plusMinutes(30)
        }
        if (changed == 1) raw else null
    }

    fun updateRole(userId: Long, role: String): Boolean = transaction(database) {
        Users.update(where = { Users.id eq userId }, body = { it[this.role] = role }) > 0
    }

    fun delete(userId: Long): Boolean {
        return transaction(database) {
            Users.selectAll().where { Users.id eq userId }.forUpdate().singleOrNull() ?: return@transaction false
            if (Projects.selectAll().where { Projects.ownerUserId eq userId }.count() > 0) throw AccountOwnsProjects()
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
        if (user["status"] != "active") return null
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
        "tenantId" to row[Users.tenantId],
        "platformOperator" to row[Users.platformOperator],
        "createdAt" to row[Users.createdAt],
    )
}
