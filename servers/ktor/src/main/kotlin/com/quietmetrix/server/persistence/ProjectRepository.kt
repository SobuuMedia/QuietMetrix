package com.quietmetrix.server.persistence

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class ProjectRepository(private val database: Database) {

    fun create(name: String, ownerUserId: Long): Pair<String, String> {
        val writeKey = "qm_wk_${UUID.randomUUID().toString().replace("-", "")}"
        val readKey = "qm_rk_${UUID.randomUUID().toString().replace("-", "")}"
        val writeKeyHash = BCrypt.withDefaults().hashToString(12, writeKey.toCharArray())
        val readKeyHash = BCrypt.withDefaults().hashToString(12, readKey.toCharArray())

        transaction(database) {
            com.quietmetrix.server.persistence.tables.Projects.insert {
                it[this.name] = name
                it[this.ownerUserId] = ownerUserId
                it[this.writeKeyHash] = writeKeyHash
                it[this.readKeyHash] = readKeyHash
            }
        }
        return Pair(writeKey, readKey)
    }

    fun findById(id: Long): Map<String, Any?>? {
        return transaction(database) {
            com.quietmetrix.server.persistence.tables.Projects
                .select { com.quietmetrix.server.persistence.tables.Projects.id eq id }
                .map { rowToMap(it) }
                .singleOrNull()
        }
    }

    fun findByWriteKeyHash(writeKey: String): Map<String, Any?>? {
        return transaction(database) {
            com.quietmetrix.server.persistence.tables.Projects.selectAll()
                .map { rowToMap(it) }
                .firstOrNull {
                    BCrypt.withDefaults().verify(writeKey.toCharArray(), it["writeKeyHash"] as String).verified
                }
        }
    }

    fun findByOwnerId(ownerUserId: Long, limit: Int = 50, offset: Int = 0): List<Map<String, Any?>> {
        return transaction(database) {
            com.quietmetrix.server.persistence.tables.Projects
                .select { com.quietmetrix.server.persistence.tables.Projects.ownerUserId eq ownerUserId }
                .limit(limit, offset.toLong())
                .map { rowToMap(it) }
        }
    }

    fun countByOwnerId(ownerUserId: Long): Long {
        return transaction(database) {
            com.quietmetrix.server.persistence.tables.Projects
                .select { com.quietmetrix.server.persistence.tables.Projects.ownerUserId eq ownerUserId }
                .count()
        }
    }

    fun validateWriteKey(writeKey: String): Long? {
        return transaction(database) {
            com.quietmetrix.server.persistence.tables.Projects.selectAll()
                .map { Pair(it[com.quietmetrix.server.persistence.tables.Projects.id], it[com.quietmetrix.server.persistence.tables.Projects.writeKeyHash]) }
                .firstOrNull { (_, hash) ->
                    BCrypt.withDefaults().verify(writeKey.toCharArray(), hash).verified
                }
                ?.first
        }
    }

    private fun rowToMap(row: ResultRow) = mapOf(
        "id" to row[com.quietmetrix.server.persistence.tables.Projects.id],
        "name" to row[com.quietmetrix.server.persistence.tables.Projects.name],
        "ownerUserId" to row[com.quietmetrix.server.persistence.tables.Projects.ownerUserId],
        "writeKeyHash" to row[com.quietmetrix.server.persistence.tables.Projects.writeKeyHash],
        "readKeyHash" to row[com.quietmetrix.server.persistence.tables.Projects.readKeyHash],
        "planId" to row[com.quietmetrix.server.persistence.tables.Projects.planId],
        "createdAt" to row[com.quietmetrix.server.persistence.tables.Projects.createdAt],
    )
}