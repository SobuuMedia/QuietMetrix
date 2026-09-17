package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Task 2 — API key regeneration + last4 + admin-wide listing. */
class ApiKeyRegenerationTest {

    private fun db(name: String): Database {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers) }
        return db
    }

    @Test
    fun `regenerate invalidates the old key and validates the new one`() {
        val db = db("regen1")
        UserRepository(db).create("owner@test.com", "password")
        val projectRepo = ProjectRepository(db)
        val originalKey = projectRepo.create("App", null, 1L)
        val projectId = transaction(db) { Projects.selectAll().first()[Projects.id] }

        assertEquals(projectId, projectRepo.validateApiKey(originalKey))

        val newKey = projectRepo.regenerateApiKey(projectId)!!
        assertNotEquals(originalKey, newKey)
        assertNull(projectRepo.validateApiKey(originalKey), "old key must stop working")
        assertEquals(projectId, projectRepo.validateApiKey(newKey), "new key must work")
    }

    @Test
    fun `create and regenerate store the last 4 chars`() {
        val db = db("regen2")
        UserRepository(db).create("owner@test.com", "password")
        val projectRepo = ProjectRepository(db)
        val originalKey = projectRepo.create("App", null, 1L)
        val projectId = transaction(db) { Projects.selectAll().first()[Projects.id] }

        assertEquals(originalKey.takeLast(4), projectRepo.findById(projectId)!!["apiKeyLast4"])

        val newKey = projectRepo.regenerateApiKey(projectId)!!
        assertEquals(newKey.takeLast(4), projectRepo.findById(projectId)!!["apiKeyLast4"])
    }

    @Test
    fun `regenerate returns null for unknown project`() {
        val db = db("regen3")
        assertNull(ProjectRepository(db).regenerateApiKey(99999L))
    }

    @Test
    fun `findAll returns projects across all owners`() {
        val db = db("regen4")
        val userRepo = UserRepository(db)
        val ownerA = userRepo.create("a@test.com", "password")
        val ownerB = userRepo.create("b@test.com", "password")
        val projectRepo = ProjectRepository(db)
        projectRepo.create("A App", null, ownerA)
        projectRepo.create("B App", null, ownerB)

        assertEquals(2, projectRepo.countAll())
        assertTrue(projectRepo.findAll().map { it["name"] }.containsAll(listOf("A App", "B App")))
        // Owner A's own-scoped view sees only their project.
        assertEquals(1, projectRepo.countByOwnerId(ownerA))
    }
}
