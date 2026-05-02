package com.quietmetrix.server.persistence

import at.favre.lib.crypto.bcrypt.BCrypt
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepository(private val database: Database) {

    fun create(email: String, password: String): Long {
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        return transaction(database) {
            (Users.insert {
                it[this.email] = email
                it[passwordHash] = hash
            } get Users.id)
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
        "createdAt" to row[Users.createdAt],
    )
}