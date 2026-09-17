package com.quietmetrix.server.counters

import com.quietmetrix.server.domain.CounterAppInfo
import com.quietmetrix.server.domain.CounterBatchRequest
import com.quietmetrix.server.domain.CounterItem
import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.CountersQuarantine
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Ktor-independent request-handling logic for POST /api/v1/counters: day clamping,
 * per-item dispatch to [CounterRepository], and accepted/quarantined tallying. CounterRoutes
 * is a thin Ktor adapter over this — see it for auth/rate-limit/payload-size concerns, which
 * are not this class's job.
 */
class CounterIngestProcessorTest {

    private fun freshRepo(): Pair<CounterRepository, Long> {
        val db = Database.connect(
            "jdbc:h2:mem:counteringest_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "",
        )
        transaction(db) { SchemaUtils.create(Users, Projects, Counters, CountersQuarantine) }
        val userRepo = UserRepository(db)
        val projectRepo = ProjectRepository(db)
        userRepo.create("admin@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        return CounterRepository(db, kThreshold = 0) to projectId
    }

    private val today = LocalDate.of(2026, 9, 4)

    @Test
    fun `every valid item is accepted and none are quarantined`() {
        val (repo, pid) = freshRepo()
        val request = CounterBatchRequest(
            day = today.toString(),
            app = CounterAppInfo(version = "2.4.0", country = "us"),
            counters = listOf(
                CounterItem(m = "screen_transition", d = mapOf("from" to "Library", "to" to "BookDetail"), n = 3, u = 1),
                CounterItem(m = "session", d = mapOf("bucket" to "1_3"), n = 1, u = 1),
            ),
        )
        val response = CounterIngestProcessor.process(pid, request, today, platform = "android", repository = repo)
        assertEquals(2, response.accepted)
        assertEquals(0, response.quarantined)

        val cells = repo.readCells(pid, "screen_transition", today, today, k = 0)
        assertEquals(1, cells.size)
        assertEquals(3L, cells[0].n)
    }

    @Test
    fun `an invalid item is quarantined without failing the rest of the batch`() {
        val (repo, pid) = freshRepo()
        val request = CounterBatchRequest(
            day = today.toString(),
            counters = listOf(
                CounterItem(m = "not_a_real_metric", d = emptyMap(), n = 1, u = 1),
                CounterItem(m = "session", d = mapOf("bucket" to "1_3"), n = 1, u = 1),
            ),
        )
        val response = CounterIngestProcessor.process(pid, request, today, platform = "ios", repository = repo)
        assertEquals(1, response.accepted)
        assertEquals(1, response.quarantined)
    }

    @Test
    fun `a day in the future is clamped to today`() {
        val (repo, pid) = freshRepo()
        val request = CounterBatchRequest(
            day = today.plusDays(10).toString(),
            counters = listOf(CounterItem(m = "session", d = mapOf("bucket" to "1_3"), n = 1, u = 1)),
        )
        CounterIngestProcessor.process(pid, request, today, platform = "jvm", repository = repo)
        assertEquals(1, repo.readCells(pid, "session", today, today, k = 0).size)
        assertEquals(0, repo.readCells(pid, "session", today.plusDays(10), today.plusDays(10), k = 0).size)
    }

    @Test
    fun `a day far in the past is clamped to today minus two days`() {
        val (repo, pid) = freshRepo()
        val request = CounterBatchRequest(
            day = today.minusDays(30).toString(),
            counters = listOf(CounterItem(m = "session", d = mapOf("bucket" to "1_3"), n = 1, u = 1)),
        )
        CounterIngestProcessor.process(pid, request, today, platform = "jvm", repository = repo)
        val clampedDay = today.minusDays(2)
        assertEquals(1, repo.readCells(pid, "session", clampedDay, clampedDay, k = 0).size)
    }

    @Test
    fun `a malformed day string falls back to today`() {
        val (repo, pid) = freshRepo()
        val request = CounterBatchRequest(
            day = "not-a-date",
            counters = listOf(CounterItem(m = "session", d = mapOf("bucket" to "1_3"), n = 1, u = 1)),
        )
        CounterIngestProcessor.process(pid, request, today, platform = "jvm", repository = repo)
        assertEquals(1, repo.readCells(pid, "session", today, today, k = 0).size)
    }
}
