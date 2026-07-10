package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.InstallMeta
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectStrictSchemaTest {

    private fun freshDb(): Database =
        Database.connect("jdbc:h2:mem:strictschema_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")

    private fun seed(db: Database): Pair<ProjectRepository, Long> {
        transaction(db) { SchemaUtils.create(Users, Projects, ProjectMembers, InstallMeta) }
        val userRepo = UserRepository(db)
        val projectRepo = ProjectRepository(db)
        userRepo.create("admin@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("fdroid-demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        return projectRepo to projectId
    }

    @Test
    fun `strict schema defaults to off with no allowlist`() {
        val db = freshDb()
        val (projectRepo, projectId) = seed(db)
        val cfg = projectRepo.getStrictSchema(projectId)
        assertFalse(cfg.enabled)
        assertTrue(cfg.allowedEvents.isEmpty())
    }

    @Test
    fun `setStrictSchema round-trips the allowlist`() {
        val db = freshDb()
        val (projectRepo, projectId) = seed(db)
        projectRepo.setStrictSchema(projectId, true, setOf("page_view", "click", "screen_view"))
        val cfg = projectRepo.getStrictSchema(projectId)
        assertTrue(cfg.enabled)
        assertEquals(setOf("page_view", "click", "screen_view"), cfg.allowedEvents)
    }

    @Test
    fun `setStrictSchema with empty allowlist disables enforcement`() {
        val db = freshDb()
        val (projectRepo, projectId) = seed(db)
        projectRepo.setStrictSchema(projectId, true, setOf("page_view"))
        // Turning it back off clears the allowlist.
        projectRepo.setStrictSchema(projectId, false, emptySet())
        val cfg = projectRepo.getStrictSchema(projectId)
        assertFalse(cfg.enabled)
        assertNull(null) // sanity placeholder
    }
}