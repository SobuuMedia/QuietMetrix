package com.quietmetrix.server.persistence

import com.quietmetrix.server.funnels.FunnelDefinition
import com.quietmetrix.server.funnels.FunnelStepDefinition
import com.quietmetrix.server.persistence.tables.Funnels
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunnelRepositoryTest {

    private fun freshDb(): Database =
        Database.connect("jdbc:h2:mem:funnelrepo_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")

    private fun seed(db: Database): Pair<FunnelRepository, Long> {
        transaction(db) { SchemaUtils.create(Users, Projects, Funnels) }
        val userRepo = UserRepository(db)
        val projectRepo = ProjectRepository(db)
        userRepo.create("admin@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        return FunnelRepository(db) to projectId
    }

    private fun definition(
        funnelKey: String = "signup",
        steps: List<FunnelStepDefinition> = listOf(
            FunnelStepDefinition(key = "view", event = "screen_view", screen = "signup"),
            FunnelStepDefinition(key = "submit", event = "signup_submitted"),
        ),
        source: String = "dashboard",
        locked: Boolean = false,
    ) = FunnelDefinition(funnelKey = funnelKey, name = "Signup", steps = steps, source = source, locked = locked)

    @Test
    fun `create then findByKey round-trips steps`() {
        val db = freshDb()
        val (funnelRepo, pid) = seed(db)
        funnelRepo.create(pid, definition())

        val found = funnelRepo.findByKey(pid, "signup")
        assertEquals("signup", found?.funnelKey)
        assertEquals(2, found?.steps?.size)
        assertEquals("screen_view", found?.steps?.get(0)?.event)
        assertEquals("signup", found?.steps?.get(0)?.screen)
        assertEquals("signup_submitted", found?.steps?.get(1)?.event)
    }

    @Test
    fun `findByKey returns null for a different project`() {
        val db = freshDb()
        val (funnelRepo, pid) = seed(db)
        funnelRepo.create(pid, definition())

        assertNull(funnelRepo.findByKey(pid + 999, "signup"))
    }

    @Test
    fun `listActive excludes archived funnels`() {
        val db = freshDb()
        val (funnelRepo, pid) = seed(db)
        funnelRepo.create(pid, definition(funnelKey = "one"))
        funnelRepo.create(pid, definition(funnelKey = "two"))
        funnelRepo.archive(pid, "one")

        val active = funnelRepo.listActive(pid)
        assertEquals(1, active.size)
        assertEquals("two", active[0].funnelKey)
    }

    @Test
    fun `countActive excludes archived funnels`() {
        val db = freshDb()
        val (funnelRepo, pid) = seed(db)
        funnelRepo.create(pid, definition(funnelKey = "one"))
        funnelRepo.create(pid, definition(funnelKey = "two"))
        assertEquals(2, funnelRepo.countActive(pid))
        funnelRepo.archive(pid, "one")
        assertEquals(1, funnelRepo.countActive(pid))
    }

    @Test
    fun `update replaces steps and sets locked`() {
        val db = freshDb()
        val (funnelRepo, pid) = seed(db)
        funnelRepo.create(pid, definition())

        val newSteps = listOf(FunnelStepDefinition(key = "only", event = "onboarded"))
        val updated = funnelRepo.update(
            pid, "signup",
            name = "Renamed", description = null, steps = newSteps, windowSeconds = 3600L, lock = true,
        )
        assertTrue(updated)

        val found = funnelRepo.findByKey(pid, "signup")!!
        assertEquals("Renamed", found.name)
        assertEquals(1, found.steps.size)
        assertEquals("onboarded", found.steps[0].event)
        assertEquals(3600L, found.windowSeconds)
        assertTrue(found.locked)
    }

    @Test
    fun `update with null fields leaves them unchanged`() {
        val db = freshDb()
        val (funnelRepo, pid) = seed(db)
        funnelRepo.create(pid, definition())

        funnelRepo.update(pid, "signup", name = null, description = null, steps = null, windowSeconds = null, lock = false)

        val found = funnelRepo.findByKey(pid, "signup")!!
        assertEquals("Signup", found.name)
        assertEquals(2, found.steps.size)
        assertFalse(found.locked)
    }

    @Test
    fun `update returns false for an archived funnel`() {
        val db = freshDb()
        val (funnelRepo, pid) = seed(db)
        funnelRepo.create(pid, definition())
        funnelRepo.archive(pid, "signup")

        val updated = funnelRepo.update(pid, "signup", name = "x", description = null, steps = null, windowSeconds = null, lock = false)
        assertFalse(updated)
    }

    @Test
    fun `archive returns false when the funnel does not exist`() {
        val db = freshDb()
        val (funnelRepo, pid) = seed(db)
        assertFalse(funnelRepo.archive(pid, "does-not-exist"))
    }

    @Test
    fun `create preserves source and locked flags`() {
        val db = freshDb()
        val (funnelRepo, pid) = seed(db)
        funnelRepo.create(pid, definition(source = "sdk", locked = true))

        val found = funnelRepo.findByKey(pid, "signup")!!
        assertEquals("sdk", found.source)
        assertTrue(found.locked)
    }
}
