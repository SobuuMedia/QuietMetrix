package com.quietmetrix.server

import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminSeederTest {

    private fun freshRepo(name: String): UserRepository {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users) }
        return UserRepository(db)
    }

    @Test
    fun `seeds an admin when none exists`() {
        val repo = freshRepo("seed1")
        val created = seedAdminUser(repo, "admin@example.com", "secret")
        assertTrue(created)
        val user = repo.findByEmail("admin@example.com")
        assertEquals("admin", user?.get("role"))
    }

    @Test
    fun `is idempotent when the admin already exists`() {
        val repo = freshRepo("seed2")
        assertTrue(seedAdminUser(repo, "admin@example.com", "secret"))
        assertFalse(seedAdminUser(repo, "admin@example.com", "secret"))
    }

    @Test
    fun `does nothing when credentials are blank`() {
        val repo = freshRepo("seed3")
        assertFalse(seedAdminUser(repo, null, "secret"))
        assertFalse(seedAdminUser(repo, "admin@example.com", ""))
    }
}
