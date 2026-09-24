package com.quietmetrix.server.persistence

import com.quietmetrix.server.experiments.ExperimentDefinition
import com.quietmetrix.server.experiments.VariantSpec
import com.quietmetrix.server.persistence.tables.Experiments
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentRepositoryTest {

    private fun freshDb(): Database =
        Database.connect(
            "jdbc:h2:mem:experimentrepo_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "",
        )

    private fun seed(db: Database): Pair<ExperimentRepository, Long> {
        transaction(db) { SchemaUtils.create(Users, Projects, Experiments) }
        val userRepo = UserRepository(db)
        val projectRepo = ProjectRepository(db)
        userRepo.create("admin@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        return ExperimentRepository(db) to projectId
    }

    private fun definition(
        key: String = "checkout_cta",
        variants: List<VariantSpec> = listOf(VariantSpec("a", 50), VariantSpec("b", 50)),
        countries: List<String>? = null,
    ) = ExperimentDefinition(
        experimentKey = key,
        name = "Checkout button colour",
        screen = "Checkout",
        trafficPercent = 100,
        variants = variants,
        countries = countries,
    )

    @Test
    fun `create then find round-trips the definition`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition())

        val found = repo.findByKey(pid, "checkout_cta")
        assertNotNull(found)
        assertEquals("checkout_cta", found.experimentKey)
        assertEquals("Checkout button colour", found.name)
        assertEquals("Checkout", found.screen)
        assertEquals("draft", found.status)
        assertEquals(100, found.trafficPercent)
        assertEquals(listOf(VariantSpec("a", 50), VariantSpec("b", 50)), found.variants)
        assertNull(found.countries)
        assertNull(found.startedAt)
        assertNull(found.endedAt)
    }

    @Test
    fun `countries round-trip when present`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition(countries = listOf("ES", "FR")))
        val found = repo.findByKey(pid, "checkout_cta")!!
        assertEquals(listOf("ES", "FR"), found.countries)
    }

    @Test
    fun `duplicate key for the same project is rejected by the unique constraint`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition())
        var threw = false
        try {
            repo.create(pid, definition())
        } catch (_: Exception) {
            threw = true
        }
        assertTrue(threw, "creating a second experiment with the same (project, key) must fail")
    }

    @Test
    fun `listActive excludes draft paused and ended`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition(key = "e1"))
        repo.create(pid, definition(key = "e2"))
        repo.create(pid, definition(key = "e3"))
        repo.update(pid, "e2", ExperimentUpdate(status = "active"))
        repo.update(pid, "e3", ExperimentUpdate(status = "active"))
        repo.update(pid, "e3", ExperimentUpdate(status = "ended"))

        val active = repo.listActive(pid)
        assertEquals(1, active.size)
        assertEquals("e2", active[0].experimentKey)
    }

    @Test
    fun `countNonEnded counts draft active and paused but not ended`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition(key = "e1")) // draft
        repo.create(pid, definition(key = "e2"))
        repo.update(pid, "e2", ExperimentUpdate(status = "active"))
        repo.create(pid, definition(key = "e3"))
        repo.update(pid, "e3", ExperimentUpdate(status = "active"))
        repo.update(pid, "e3", ExperimentUpdate(status = "paused"))
        repo.create(pid, definition(key = "e4"))
        repo.update(pid, "e4", ExperimentUpdate(status = "active"))
        repo.update(pid, "e4", ExperimentUpdate(status = "ended"))

        assertEquals(3L, repo.countNonEnded(pid))
    }

    @Test
    fun `started_at is set only on the first transition to active`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition())
        val afterFirstActive = repo.update(pid, "checkout_cta", ExperimentUpdate(status = "active"))!!
        val firstStartedAt = afterFirstActive.startedAt
        assertNotNull(firstStartedAt)

        repo.update(pid, "checkout_cta", ExperimentUpdate(status = "paused"))
        val afterSecondActive = repo.update(pid, "checkout_cta", ExperimentUpdate(status = "active"))!!
        assertEquals(firstStartedAt, afterSecondActive.startedAt)
    }

    @Test
    fun `ended_at is set on transition to ended`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition())
        repo.update(pid, "checkout_cta", ExperimentUpdate(status = "active"))
        val ended = repo.update(pid, "checkout_cta", ExperimentUpdate(status = "ended"))!!
        assertNotNull(ended.endedAt)
    }

    @Test
    fun `deleteDraft removes a draft and returns true`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition())
        assertTrue(repo.deleteDraft(pid, "checkout_cta"))
        assertNull(repo.findByKey(pid, "checkout_cta"))
    }

    @Test
    fun `deleteDraft returns false for a non-draft experiment and leaves it intact`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition())
        repo.update(pid, "checkout_cta", ExperimentUpdate(status = "active"))
        assertFalse(repo.deleteDraft(pid, "checkout_cta"))
        assertNotNull(repo.findByKey(pid, "checkout_cta"))
    }

    @Test
    fun `update partially changes only the given fields`() {
        val (repo, pid) = seed(freshDb())
        repo.create(pid, definition())
        val updated = repo.update(pid, "checkout_cta", ExperimentUpdate(trafficPercent = 50))!!
        assertEquals(50, updated.trafficPercent)
        assertEquals("Checkout button colour", updated.name) // unchanged
        assertEquals("Checkout", updated.screen) // unchanged
    }
}
