package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.CountersQuarantine
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CounterRepositoryTest {

    private fun freshDb(): Database =
        Database.connect(
            "jdbc:h2:mem:counterrepo_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "",
        )

    private fun seed(db: Database): Pair<CounterRepository, Long> {
        transaction(db) { SchemaUtils.create(Users, Projects, Counters, CountersQuarantine) }
        val userRepo = UserRepository(db)
        val projectRepo = ProjectRepository(db)
        userRepo.create("admin@quietmetrix.com", "password123")
        val apiKey = projectRepo.create("demo", null, 1L)
        val projectId = projectRepo.validateApiKey(apiKey)!!
        return CounterRepository(db, kThreshold = 3, maxDistinctCellsPerMetric = 2) to projectId
    }

    private val day = LocalDate.of(2026, 9, 4)

    private fun delta(
        metric: String = "screen_transition",
        dims: Map<String, String> = mapOf("from" to "Library", "to" to "BookDetail"),
        n: Long = 1L,
        isNewDevice: Boolean = true,
    ) = CounterRepository.CounterDelta(metric = metric, dims = dims, n = n, isNewDevice = isNewDevice)

    @Test
    fun `a fresh cell is inserted with its first delta`() {
        val (repo, pid) = seed(freshDb())
        val result = repo.upsert(pid, day, delta(n = 3L, isNewDevice = true))
        assertTrue(result is CounterRepository.UpsertResult.Applied)

        val cells = repo.readCells(pid, "screen_transition", day, day, k = 0)
        assertEquals(1, cells.size)
        assertEquals(3L, cells[0].n)
        assertEquals(1L, cells[0].devices)
        assertEquals(mapOf("from" to "Library", "to" to "BookDetail"), cells[0].dims)
    }

    @Test
    fun `a second delta to the same cell merges n and devices additively`() {
        val (repo, pid) = seed(freshDb())
        repo.upsert(pid, day, delta(n = 2L, isNewDevice = true))
        repo.upsert(pid, day, delta(n = 5L, isNewDevice = true))
        repo.upsert(pid, day, delta(n = 1L, isNewDevice = false))

        val cells = repo.readCells(pid, "screen_transition", day, day, k = 0)
        assertEquals(1, cells.size)
        assertEquals(8L, cells[0].n)
        assertEquals(2L, cells[0].devices)
    }

    @Test
    fun `an unknown metric is quarantined and never written to counters`() {
        val (repo, pid) = seed(freshDb())
        val result = repo.upsert(pid, day, delta(metric = "not_a_real_metric"))
        assertTrue(result is CounterRepository.UpsertResult.Quarantined)
        assertEquals("unknown_metric", result.reason)
        assertTrue(repo.readCells(pid, "not_a_real_metric", day, day, k = 0).isEmpty())
        assertEquals(1L, repo.countQuarantined(pid))
    }

    @Test
    fun `a malformed dim value is quarantined`() {
        val (repo, pid) = seed(freshDb())
        val result = repo.upsert(pid, day, delta(dims = mapOf("from" to "Library", "to" to "Bad Value!")))
        assertTrue(result is CounterRepository.UpsertResult.Quarantined)
        assertEquals("dim_value_invalid", result.reason)
    }

    @Test
    fun `a new distinct cell beyond the cardinality cap is quarantined, existing cells still update`() {
        val (repo, pid) = seed(freshDb())
        // cap is 2 distinct cells per metric for this repo instance
        repo.upsert(pid, day, delta(dims = mapOf("from" to "A", "to" to "B")))
        repo.upsert(pid, day, delta(dims = mapOf("from" to "B", "to" to "C")))
        val third = repo.upsert(pid, day, delta(dims = mapOf("from" to "C", "to" to "D")))
        assertTrue(third is CounterRepository.UpsertResult.Quarantined)
        assertEquals("cardinality_cap", third.reason)

        // existing cell still accepts further updates despite the cap being reached
        val again = repo.upsert(pid, day, delta(dims = mapOf("from" to "A", "to" to "B"), n = 4L))
        assertTrue(again is CounterRepository.UpsertResult.Applied)

        assertEquals(2, repo.readCells(pid, "screen_transition", day, day, k = 0).size)
    }

    @Test
    fun `k-anonymity read filter excludes cells below the threshold and includes cells at or above it`() {
        val (repo, pid) = seed(freshDb()) // kThreshold = 3
        // 2 distinct devices for one cell (below k=3)
        repo.upsert(pid, day, delta(dims = mapOf("from" to "A", "to" to "B"), isNewDevice = true))
        repo.upsert(pid, day, delta(dims = mapOf("from" to "A", "to" to "B"), isNewDevice = true))
        // 3 distinct devices for another cell (at k=3)
        repo.upsert(pid, day, delta(dims = mapOf("from" to "B", "to" to "C"), isNewDevice = true))
        repo.upsert(pid, day, delta(dims = mapOf("from" to "B", "to" to "C"), isNewDevice = true))
        repo.upsert(pid, day, delta(dims = mapOf("from" to "B", "to" to "C"), isNewDevice = true))

        val visible = repo.readCells(pid, "screen_transition", day, day)
        assertEquals(1, visible.size)
        assertEquals(mapOf("from" to "B", "to" to "C"), visible[0].dims)
    }

    private fun eventDelta(name: String = "page_view", platform: String = "android", n: Long = 1L, isNewDevice: Boolean = true) =
        CounterRepository.CounterDelta(metric = "event", dims = mapOf("name" to name), platform = platform, n = n, isNewDevice = isNewDevice)

    // --- dailyTotals: day-preserving reads for a trend chart, unlike readCells which collapses
    // the whole range into one number per dims_hash. ------------------------------------------

    @Test
    fun `dailyTotals preserves per-day counts instead of collapsing the range`() {
        val (repo, pid) = seed(freshDb()) // kThreshold = 3
        val day2 = day.plusDays(1)
        repeat(3) { repo.upsert(pid, day, eventDelta(n = 2L, isNewDevice = true)) }
        repeat(3) { repo.upsert(pid, day2, eventDelta(n = 5L, isNewDevice = true)) }

        val totals = repo.dailyTotals(pid, "event", day, day2, k = 3).associateBy { it.day }
        assertEquals(6L, totals.getValue(day).n)
        assertEquals(15L, totals.getValue(day2).n)
    }

    @Test
    fun `dailyTotals applies the k-anonymity gate per day, not across the whole range`() {
        val (repo, pid) = seed(freshDb()) // kThreshold = 3
        val day2 = day.plusDays(1)
        // Only 2 distinct devices on day 1 (below k=3) -- even though summed across both days
        // there would be enough, day 1 alone must not be included.
        repeat(2) { repo.upsert(pid, day, eventDelta(isNewDevice = true)) }
        repeat(3) { repo.upsert(pid, day2, eventDelta(isNewDevice = true)) }

        val totals = repo.dailyTotals(pid, "event", day, day2, k = 3)
        assertEquals(setOf(day2), totals.map { it.day }.toSet())
    }

    @Test
    fun `dailyTotals sums across distinct event names for the same day`() {
        val (repo, pid) = seed(freshDb())
        repeat(3) { repo.upsert(pid, day, eventDelta(name = "page_view", isNewDevice = true)) }
        repeat(3) { repo.upsert(pid, day, eventDelta(name = "button_click", isNewDevice = true)) }

        val totals = repo.dailyTotals(pid, "event", day, day, k = 3)
        assertEquals(1, totals.size)
        assertEquals(6L, totals[0].n)
    }

    // --- totalsByPlatform: a coarse per-platform breakdown, grouping by the platform column
    // rather than by dims. ----------------------------------------------------------------------

    @Test
    fun `totalsByPlatform groups n by platform`() {
        val (repo, pid) = seed(freshDb())
        repeat(3) { repo.upsert(pid, day, eventDelta(platform = "android", isNewDevice = true)) }
        repeat(3) { repo.upsert(pid, day, eventDelta(platform = "ios", isNewDevice = true)) }

        val totals = repo.totalsByPlatform(pid, "event", day, day, k = 3).associateBy { it.platform }
        assertEquals(3L, totals.getValue("android").n)
        assertEquals(3L, totals.getValue("ios").n)
    }

    @Test
    fun `totalsByPlatform applies the k-anonymity gate per platform cell`() {
        val (repo, pid) = seed(freshDb()) // kThreshold = 3
        repeat(2) { repo.upsert(pid, day, eventDelta(platform = "android", isNewDevice = true)) }
        repeat(3) { repo.upsert(pid, day, eventDelta(platform = "ios", isNewDevice = true)) }

        val totals = repo.totalsByPlatform(pid, "event", day, day, k = 3)
        assertEquals(setOf("ios"), totals.map { it.platform }.toSet())
    }
}
