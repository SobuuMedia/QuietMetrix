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

/**
 * Task 5 — an agent that retries a timed-out `POST /projects` must not mint a second
 * project/key. `Idempotency-Key` is scoped per-owner: two different owners may reuse the
 * same key without colliding.
 */
class ProjectIdempotencyTest {

    private fun db(name: String): Database {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers) }
        return db
    }

    @Test
    fun `findByOwnerAndIdempotencyKey finds a project created with that key`() {
        val db = db("idem1")
        val ownerId = UserRepository(db).create("owner@test.com", "password")
        val repo = ProjectRepository(db)
        repo.create("App", null, ownerId, idempotencyKey = "req-123")

        val found = repo.findByOwnerAndIdempotencyKey(ownerId, "req-123")
        assertEquals("App", found?.get("name"))
    }

    @Test
    fun `unknown idempotency key finds nothing`() {
        val db = db("idem2")
        val ownerId = UserRepository(db).create("owner@test.com", "password")
        assertNull(ProjectRepository(db).findByOwnerAndIdempotencyKey(ownerId, "no-such-key"))
    }

    @Test
    fun `same key for a different owner does not collide`() {
        val db = db("idem3")
        val userRepo = UserRepository(db)
        val ownerA = userRepo.create("a@test.com", "password")
        val ownerB = userRepo.create("b@test.com", "password")
        val repo = ProjectRepository(db)
        repo.create("A App", null, ownerA, idempotencyKey = "shared-key")
        repo.create("B App", null, ownerB, idempotencyKey = "shared-key")

        assertEquals("A App", repo.findByOwnerAndIdempotencyKey(ownerA, "shared-key")?.get("name"))
        assertEquals("B App", repo.findByOwnerAndIdempotencyKey(ownerB, "shared-key")?.get("name"))
    }

    @Test
    fun `create without an idempotency key never collides with itself`() {
        val db = db("idem4")
        val ownerId = UserRepository(db).create("owner@test.com", "password")
        val repo = ProjectRepository(db)
        val keyA = repo.create("First", null, ownerId)
        val keyB = repo.create("Second", null, ownerId)

        assertNotEquals(keyA, keyB)
        assertEquals(2, repo.countByOwnerId(ownerId))
    }
}
