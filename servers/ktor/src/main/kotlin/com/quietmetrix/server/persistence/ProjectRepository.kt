package com.quietmetrix.server.persistence

import at.favre.lib.crypto.bcrypt.BCrypt
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
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

    fun create(name: String, ownerUserId: Long): Pair<String, String> {
        val apiKey = "qm_ak_${UUID.randomUUID().toString().replace("-", "")}"
        val apiKeyHash = BCrypt.withDefaults().hashToString(12, apiKey.toCharArray())
        val apiKeySha256 = sha256(apiKey)

        transaction(database) {
            Projects.insert {
                it[this.name] = name
                it[this.ownerUserId] = ownerUserId
                it[this.apiKeyHash] = apiKeyHash
                it[this.apiKeySha256] = apiKeySha256
            }
        }
        return Pair(apiKey, apiKey)
    }

    fun findById(id: Long): Map<String, Any?>? {
        return transaction(database) {
            Projects
                .selectAll()
                .where { (Projects.id eq id) and (Projects.deletedAt.isNull()) }
                .map { rowToMap(it) }
                .singleOrNull()
        }
    }

    fun findByApiKeyHash(apiKey: String): Map<String, Any?>? {
        return transaction(database) {
            Projects.selectAll()
                .where { Projects.deletedAt.isNull() }
                .map { rowToMap(it) }
                .firstOrNull {
                    BCrypt.verifyer().verify(apiKey.toCharArray(), it["apiKeyHash"] as String).verified
                }
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
        "ownerUserId" to row[Projects.ownerUserId],
        "apiKeyHash" to row[Projects.apiKeyHash],
        "planId" to row[Projects.planId],
        "createdAt" to row[Projects.createdAt],
    )
}
