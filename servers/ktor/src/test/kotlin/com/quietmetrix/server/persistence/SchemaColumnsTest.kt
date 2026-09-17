package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Task 1 — new role/status/invite + api_key_last4 columns exist with the right defaults. */
class SchemaColumnsTest {

    @Test
    fun `new user defaults to admin role and active status`() {
        val db = Database.connect("jdbc:h2:mem:schema1;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers) }
        val userRepo = UserRepository(db)
        userRepo.create("schema@test.com", "password")

        val user = userRepo.findByEmail("schema@test.com")!!
        assertEquals("admin", user["role"])
        assertEquals("active", user["status"])
    }

    @Test
    fun `projects api_key_last4 column is present and nullable`() {
        val db = Database.connect("jdbc:h2:mem:schema2;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers) }

        val last4 = transaction(db) {
            val ownerId = Users.insert {
                it[email] = "o@test.com"
                it[passwordHash] = "x"
            } get Users.id
            Projects.insert {
                it[name] = "App"
                it[ownerUserId] = ownerId
                it[apiKeyHash] = "h"
            }
            Projects.selectAll().first()[Projects.apiKeyLast4]
        }
        assertNull(last4)
    }
}
