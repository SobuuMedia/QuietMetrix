package com.quietmetrix.server.funnels

import com.quietmetrix.server.persistence.FunnelRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.Funnels
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunnelRegistrationServiceTest {

    private fun freshRepo(): Pair<FunnelRepository, Long> {
        val db = Database.connect("jdbc:h2:mem:funnelreg_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, Funnels) }
        UserRepository(db).create("admin@test.com", "password")
        val projectRepo = ProjectRepository(db)
        val apiKey = projectRepo.create("demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        return FunnelRepository(db) to projectId
    }

    private fun definition(
        key: String = "signup",
        steps: List<FunnelStepDefinition> = listOf(
            FunnelStepDefinition(key = "view", event = "screen_view"),
            FunnelStepDefinition(key = "submit", event = "signup_submitted"),
        ),
    ) = FunnelDefinition(funnelKey = key, name = "Signup", steps = steps)

    @Test
    fun `registers a brand-new funnel with source sdk`() {
        val (repo, pid) = freshRepo()
        val service = FunnelRegistrationService(repo)

        val result = service.register(pid, listOf(definition()))

        assertEquals(listOf("signup"), result.registered)
        assertTrue(result.skippedLocked.isEmpty())
        assertTrue(result.rejected.isEmpty())
        val stored = repo.findByKey(pid, "signup")!!
        assertEquals("sdk", stored.source)
        assertEquals(false, stored.locked)
    }

    @Test
    fun `re-registering an identical definition is idempotent`() {
        val (repo, pid) = freshRepo()
        val service = FunnelRegistrationService(repo)

        service.register(pid, listOf(definition()))
        val second = service.register(pid, listOf(definition()))

        assertEquals(listOf("signup"), second.registered)
        assertEquals(1, repo.countActive(pid))
        val stored = repo.findByKey(pid, "signup")!!
        assertEquals(2, stored.steps.size)
    }

    private fun threeStepReplacement() = listOf(
        FunnelStepDefinition(key = "start", event = "onboarding_started"),
        FunnelStepDefinition(key = "middle", event = "onboarding_step_2"),
        FunnelStepDefinition(key = "done", event = "onboarded"),
    )

    @Test
    fun `re-registering with changed steps updates the definition`() {
        val (repo, pid) = freshRepo()
        val service = FunnelRegistrationService(repo)
        service.register(pid, listOf(definition()))

        val changed = definition(steps = threeStepReplacement())
        service.register(pid, listOf(changed))

        val stored = repo.findByKey(pid, "signup")!!
        assertEquals(3, stored.steps.size)
        assertEquals("onboarded", stored.steps[2].event)
    }

    @Test
    fun `skips a funnel that was locked by a dashboard edit`() {
        val (repo, pid) = freshRepo()
        repo.create(pid, definition().copy(source = "dashboard", locked = true))
        val service = FunnelRegistrationService(repo)

        val changed = definition(steps = threeStepReplacement())
        val result = service.register(pid, listOf(changed))

        assertTrue(result.registered.isEmpty())
        assertEquals(listOf("signup"), result.skippedLocked)
        // Unchanged — the locked dashboard edit was not overwritten.
        val stored = repo.findByKey(pid, "signup")!!
        assertEquals(2, stored.steps.size)
        assertEquals("dashboard", stored.source)
    }

    @Test
    fun `rejects an individually invalid funnel but registers the rest of the batch`() {
        val (repo, pid) = freshRepo()
        val service = FunnelRegistrationService(repo)

        val bad = definition(key = "Bad Key")
        val good = definition(key = "ok")
        val result = service.register(pid, listOf(bad, good))

        assertEquals(listOf("ok"), result.registered)
        assertTrue(result.rejected.containsKey("Bad Key"))
    }

    @Test
    fun `rejects a new funnel that would exceed the per-project cap`() {
        val (repo, pid) = freshRepo()
        repeat(20) { i -> repo.create(pid, definition(key = "existing-$i")) }
        val service = FunnelRegistrationService(repo)

        val result = service.register(pid, listOf(definition(key = "one-too-many")))

        assertTrue(result.registered.isEmpty())
        assertTrue(result.rejected.containsKey("one-too-many"))
        assertEquals(20, repo.countActive(pid))
    }

    @Test
    fun `updating an existing funnel does not count against its own cap slot`() {
        val (repo, pid) = freshRepo()
        repeat(19) { i -> repo.create(pid, definition(key = "existing-$i")) }
        repo.create(pid, definition(key = "signup"))
        val service = FunnelRegistrationService(repo)

        // 20 funnels already exist, including "signup" itself — re-registering it must not
        // be rejected as if it were a 21st funnel.
        val result = service.register(pid, listOf(definition(key = "signup")))

        assertEquals(listOf("signup"), result.registered)
    }
}
