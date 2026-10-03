package com.quietmetrix.server.experiments

import com.quietmetrix.server.persistence.ExperimentRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.Experiments
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import com.quietmetrix.server.ratelimit.QuotaEnforcer
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentServiceTest {

    private fun freshDb(): Database =
        Database.connect(
            "jdbc:h2:mem:experimentservice_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "",
        )

    private fun seed(db: Database): Pair<Long, ExperimentRepository> {
        transaction(db) { SchemaUtils.create(Users, Projects, Experiments) }
        UserRepository(db).create("admin@quietmetrix.com", "password123")
        val projectRepo = ProjectRepository(db)
        val apiKey = projectRepo.create("demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        return projectId to ExperimentRepository(db)
    }

    private fun setPlan(db: Database, projectId: Long, planId: String?) {
        transaction(db) {
            Projects.update({ Projects.id eq projectId }) { it[this.planId] = planId }
        }
    }

    private fun service(db: Database, repo: ExperimentRepository, isCloud: Boolean, withQuota: Boolean = true): ExperimentService =
        ExperimentService(repo, ProjectRepository(db), if (withQuota) QuotaEnforcer(ProjectRepository(db)) else null, isCloud)

    private fun createRequest(key: String = "checkout_cta") =
        CreateExperimentRequest(key = key, name = "Checkout button colour")

    /** Rollout selects a declared variant while preserving historical allocation and freeing quota. */
    @Test
    fun `shipping validates choice preserves split and publishes full rollout`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val svc = service(db, repo, isCloud = true)
        svc.create(pid, createRequest().copy(trafficPercent = 30, countries = listOf("ES")))
        svc.update(pid, "checkout_cta", UpdateExperimentRequest(status = "active"))
        for (choice in listOf(null, "unknown")) {
            assertIs<ExperimentOutcome.Error>(svc.update(pid, "checkout_cta", UpdateExperimentRequest(status = "shipped", selectedVariant = choice)))
        }
        val shipped = assertIs<ExperimentOutcome.Ok<com.quietmetrix.server.persistence.ExperimentRecord>>(
            svc.update(pid, "checkout_cta", UpdateExperimentRequest(status = "shipped", selectedVariant = "a")),
        ).value
        assertEquals("a", shipped.selectedVariant)
        assertEquals(DEFAULT_VARIANTS, shipped.variants)
        assertEquals(30, shipped.trafficPercent)
        assertEquals(listOf("ES"), shipped.countries)
        val config = svc.activeConfig(pid).single().toConfigDto()
        assertEquals(100, config.trafficPercent)
        assertEquals(listOf(VariantSpec("a", 100), VariantSpec("b", 0)), config.variants)
        assertNull(config.countries)
        assertEquals(0L, repo.countNonEnded(pid))
        assertIs<ExperimentOutcome.Error>(svc.update(pid, "checkout_cta", UpdateExperimentRequest(status = "closed")))
        assertIs<ExperimentOutcome.Error>(svc.update(pid, "checkout_cta", UpdateExperimentRequest()))
    }

    /** Legacy finished records can close without inventing a selection. */
    @Test
    fun `legacy ended closes without choice and stale transition cannot overwrite it`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val svc = service(db, repo, isCloud = false)
        svc.create(pid, createRequest())
        svc.update(pid, "checkout_cta", UpdateExperimentRequest(status = "ended"))
        assertNull(repo.findByKey(pid, "checkout_cta")!!.selectedVariant)
        val closed = assertIs<ExperimentOutcome.Ok<com.quietmetrix.server.persistence.ExperimentRecord>>(
            svc.update(pid, "checkout_cta", UpdateExperimentRequest(status = "closed")),
        ).value
        assertNull(closed.selectedVariant)
        assertNull(repo.update(pid, "checkout_cta", com.quietmetrix.server.persistence.ExperimentUpdate(
            status = "shipped", selectedVariant = "b", expectedStatus = "ended",
        )))
        assertEquals("closed", repo.findByKey(pid, "checkout_cta")!!.status)
        assertTrue(svc.activeConfig(pid).isEmpty())
        assertEquals(0L, repo.countNonEnded(pid))
    }

    @Test
    fun `create succeeds and returns a draft`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val outcome = service(db, repo, isCloud = false).create(pid, createRequest())
        assertIs<ExperimentOutcome.Ok<com.quietmetrix.server.persistence.ExperimentRecord>>(outcome)
        val record = outcome.value
        assertEquals("draft", record.status)
        assertEquals(DEFAULT_VARIANTS, record.variants)
        assertEquals(100, record.trafficPercent)
    }

    @Test
    fun `create with invalid data returns schema_violation`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val outcome = service(db, repo, isCloud = false).create(pid, createRequest().copy(key = "BAD KEY"))
        assertIs<ExperimentOutcome.Error>(outcome)
        assertEquals(400, outcome.status)
        assertEquals("schema_violation", outcome.code)
    }

    @Test
    fun `create with a duplicate key returns experiment_exists`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val svc = service(db, repo, isCloud = false)
        svc.create(pid, createRequest())
        val outcome = svc.create(pid, createRequest())
        assertIs<ExperimentOutcome.Error>(outcome)
        assertEquals(409, outcome.status)
        assertEquals("experiment_exists", outcome.code)
    }

    @Test
    fun `a third non-ended experiment on a free CLOUD project is rejected with experiment_limit_reached`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        setPlan(db, pid, null) // no plan set = free
        val svc = service(db, repo, isCloud = true)
        assertIs<ExperimentOutcome.Ok<*>>(svc.create(pid, createRequest("e1")))
        assertIs<ExperimentOutcome.Ok<*>>(svc.create(pid, createRequest("e2")))
        val third = svc.create(pid, createRequest("e3"))
        assertIs<ExperimentOutcome.Error>(third)
        assertEquals(403, third.status)
        assertEquals("experiment_limit_reached", third.code)
    }

    @Test
    fun `the same sequence on SELFHOST is never capped`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        setPlan(db, pid, null)
        val svc = service(db, repo, isCloud = false)
        assertIs<ExperimentOutcome.Ok<*>>(svc.create(pid, createRequest("e1")))
        assertIs<ExperimentOutcome.Ok<*>>(svc.create(pid, createRequest("e2")))
        assertIs<ExperimentOutcome.Ok<*>>(svc.create(pid, createRequest("e3")))
    }

    @Test
    fun `a paid plan on CLOUD is unlimited`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        setPlan(db, pid, "pro")
        val svc = service(db, repo, isCloud = true)
        assertIs<ExperimentOutcome.Ok<*>>(svc.create(pid, createRequest("e1")))
        assertIs<ExperimentOutcome.Ok<*>>(svc.create(pid, createRequest("e2")))
        assertIs<ExperimentOutcome.Ok<*>>(svc.create(pid, createRequest("e3")))
    }

    @Test
    fun `ending one experiment frees a slot for a free CLOUD project`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        setPlan(db, pid, "free")
        val svc = service(db, repo, isCloud = true)
        svc.create(pid, createRequest("e1"))
        svc.create(pid, createRequest("e2"))
        svc.update(pid, "e1", UpdateExperimentRequest(status = "active"))
        svc.update(pid, "e1", UpdateExperimentRequest(status = "ended"))
        val outcome = svc.create(pid, createRequest("e3"))
        assertIs<ExperimentOutcome.Ok<*>>(outcome)
    }

    @Test
    fun `editing variants on an active experiment is rejected as not_editable`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val svc = service(db, repo, isCloud = false)
        svc.create(pid, createRequest())
        svc.update(pid, "checkout_cta", UpdateExperimentRequest(status = "active"))
        val outcome = svc.update(pid, "checkout_cta", UpdateExperimentRequest(variants = listOf(VariantSpec("x", 50), VariantSpec("y", 50))))
        assertIs<ExperimentOutcome.Error>(outcome)
        assertEquals(409, outcome.status)
        assertEquals("not_editable", outcome.code)
    }

    @Test
    fun `editing name on an active experiment is allowed`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val svc = service(db, repo, isCloud = false)
        svc.create(pid, createRequest())
        svc.update(pid, "checkout_cta", UpdateExperimentRequest(status = "active"))
        val outcome = svc.update(pid, "checkout_cta", UpdateExperimentRequest(name = "New name"))
        assertIs<ExperimentOutcome.Ok<com.quietmetrix.server.persistence.ExperimentRecord>>(outcome)
        assertEquals("New name", outcome.value.name)
    }

    @Test
    fun `an invalid status transition is rejected`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val svc = service(db, repo, isCloud = false)
        svc.create(pid, createRequest())
        val outcome = svc.update(pid, "checkout_cta", UpdateExperimentRequest(status = "paused"))
        assertIs<ExperimentOutcome.Error>(outcome)
        assertEquals(409, outcome.status)
        assertEquals("invalid_transition", outcome.code)
    }

    @Test
    fun `delete only succeeds for a draft experiment`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val svc = service(db, repo, isCloud = false)
        svc.create(pid, createRequest("e1"))
        svc.create(pid, createRequest("e2"))
        svc.update(pid, "e2", UpdateExperimentRequest(status = "active"))

        assertIs<ExperimentOutcome.Ok<*>>(svc.delete(pid, "e1"))
        val blocked = svc.delete(pid, "e2")
        assertIs<ExperimentOutcome.Error>(blocked)
        assertEquals(409, blocked.status)
        assertEquals("not_editable", blocked.code)
    }

    @Test
    fun `list returns limit 2 on CLOUD free and null otherwise`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        setPlan(db, pid, null)
        assertEquals(2, service(db, repo, isCloud = true).list(pid).limit)
        assertNull(service(db, repo, isCloud = false).list(pid).limit)
    }

    @Test
    fun `activeConfig returns only active experiments`() {
        val db = freshDb()
        val (pid, repo) = seed(db)
        val svc = service(db, repo, isCloud = false)
        svc.create(pid, createRequest("e1"))
        svc.create(pid, createRequest("e2"))
        svc.update(pid, "e2", UpdateExperimentRequest(status = "active"))

        val active = svc.activeConfig(pid)
        assertEquals(1, active.size)
        assertEquals("e2", active[0].experimentKey)
    }
}
