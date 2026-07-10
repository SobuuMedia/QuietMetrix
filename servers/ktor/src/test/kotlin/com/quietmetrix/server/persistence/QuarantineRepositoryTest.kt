package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.EventsQuarantine
import com.quietmetrix.server.persistence.tables.InstallMeta
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuarantineRepositoryTest {

    private fun freshDb(): Database =
        Database.connect("jdbc:h2:mem:qtest_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")

    private fun seed(db: Database): Pair<ProjectRepository, Long> {
        transaction(db) { SchemaUtils.create(Users, Projects, InstallMeta, EventsQuarantine) }
        val userRepo = UserRepository(db)
        val projectRepo = ProjectRepository(db)
        userRepo.create("admin@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("fdroid-demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        return projectRepo to projectId
    }

    @Test
    fun `insert and list quarantined events`() {
        val db = freshDb()
        val (_, projectId) = seed(db)
        val repo = QuarantineRepository(db)
        repo.insert(projectId, """{"event":"page_view"}""", "ramp-up", "auto-revoked", "1.2.3.4", "abc123")
        repo.insert(projectId, """{"event":"click"}""", "ramp-up", "auto-revoked", "1.2.3.4", "abc123")
        val list = repo.list(projectId, 50)
        assertEquals(2, list.size)
    }

    @Test
    fun `release deletes from quarantine`() {
        val db = freshDb()
        val (_, projectId) = seed(db)
        val repo = QuarantineRepository(db)
        val qid = repo.insert(projectId, """{"event":"page_view"}""", "ramp-up", "test", "1.2.3.4", "abc")
        // Release just returns success; the caller handles re-insertion into events.
        assertTrue(repo.release(qid))
        assertTrue(repo.list(projectId, 10).isEmpty())
    }

    @Test
    fun `deleteById removes the record`() {
        val db = freshDb()
        val (_, projectId) = seed(db)
        val repo = QuarantineRepository(db)
        val qid = repo.insert(projectId, """{"event":"page_view"}""", "ramp-up", "test", "1.2.3.4", "abc")
        assertTrue(repo.deleteById(qid))
        assertTrue(repo.list(projectId, 10).isEmpty())
    }
}
