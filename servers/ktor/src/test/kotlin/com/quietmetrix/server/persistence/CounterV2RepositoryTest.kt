package com.quietmetrix.server.persistence

import com.quietmetrix.server.domain.CounterV2BatchRequest
import com.quietmetrix.server.domain.CounterV2EnvelopeItem
import com.quietmetrix.server.domain.CounterV2EnvelopeRequest
import com.quietmetrix.server.domain.CounterV2Item
import com.quietmetrix.server.domain.CounterV2Segment
import com.quietmetrix.server.domain.SdkInfo
import com.quietmetrix.server.persistence.tables.CounterBatchReceiptsV2
import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CounterV2RepositoryTest {
    private fun setup(): Triple<CounterV2Repository, Database, Long> {
        val db = Database.connect("jdbc:h2:mem:counterv2_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, CounterCellsV2, CounterBatchReceiptsV2) }
        UserRepository(db).create("v2@example.com", "password123")
        val key = ProjectRepository(db).create("v2", null, 1L)
        return Triple(CounterV2Repository(db), db, ProjectRepository(db).validateApiKey(key)!!)
    }

    private fun request(batchId: String = "batch_0000000000001", hour: Int? = 9, counters: List<CounterV2Item> = listOf(
        CounterV2Item("screen_view_v2", mapOf("screen" to "home"), 3, 1),
    )) = CounterV2BatchRequest(batchId, day = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString(), hour = hour, counters = counters)

    @Test fun `hourly cells persist and same batch replay does not increment twice`() {
        val (repo, db, pid) = setup()
        assertEquals(CounterV2Repository.Result.Accepted(1, false), repo.apply(pid, request(), "a".repeat(32)))
        assertEquals(CounterV2Repository.Result.Accepted(1, true), repo.apply(pid, request(), "b".repeat(32)))
        val row = transaction(db) { CounterCellsV2.selectAll().single() }
        assertEquals(9.toShort(), row[CounterCellsV2.hour])
        assertEquals(3L, row[CounterCellsV2.n])
        assertEquals(1L, row[CounterCellsV2.contributors])
    }

    @Test fun `same batch id with a changed body conflicts`() {
        val (repo, _, pid) = setup()
        repo.apply(pid, request(), "a".repeat(32))
        val result = repo.apply(pid, request(counters = listOf(CounterV2Item("screen_view_v2", mapOf("screen" to "search"), 1))), "b".repeat(32))
        assertIs<CounterV2Repository.Result.Conflict>(result)
    }

    @Test fun `invalid item rejects complete batch without writing valid siblings`() {
        val (repo, db, pid) = setup()
        val result = repo.apply(pid, request(counters = listOf(
            CounterV2Item("screen_view_v2", mapOf("screen" to "home"), 1),
            CounterV2Item("unknown_metric", emptyMap(), 1),
        )), "a".repeat(32))
        assertIs<CounterV2Repository.Result.Invalid>(result)
        assertEquals(0, transaction(db) { CounterCellsV2.selectAll().count() })
    }

    @Test fun `schema two accepts per item day hour and segment with deterministic payload identity`() {
        val (repo, db, pid) = setup()
        val request = CounterV2EnvelopeRequest(
            schema = 2,
            batchId = "schema2_batch_00001",
            sdk = SdkInfo("android", "2.1"),
            items = listOf(
                CounterV2EnvelopeItem(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString(), 9, "screen_view_v2", CounterV2Segment("global", "all"), mapOf("screen" to "home"), 2),
                CounterV2EnvelopeItem(LocalDate.now(java.time.ZoneOffset.UTC).toString(), null, "screen_view_v2", CounterV2Segment("platform", "android"), mapOf("screen" to "home"), 1),
            ),
        )
        assertEquals(repo.canonicalPayloadHash(request), repo.canonicalPayloadHash(request.copy(items = request.items.reversed())))
        assertEquals(CounterV2Repository.Result.Accepted(2, false), repo.apply(pid, request, "a".repeat(32)))
        assertEquals(CounterV2Repository.Result.Accepted(2, true), repo.apply(pid, request, "b".repeat(32)))
        val rows = transaction(db) { CounterCellsV2.selectAll().toList() }
        assertEquals(2, rows.size)
        assertEquals(setOf("global" to "all", "platform" to "android"), rows.map { it[CounterCellsV2.segmentKind] to it[CounterCellsV2.segmentValue] }.toSet())
        assertEquals(setOf((-1).toShort(), 9.toShort()), rows.map { it[CounterCellsV2.hour] }.toSet())
    }

    @Test fun `schema two rejects a bad segment atomically`() {
        val (repo, db, pid) = setup()
        val request = CounterV2EnvelopeRequest(
            schema = 2, batchId = "schema2_invalid_0001", sdk = SdkInfo("android", "2.1"),
            items = listOf(
                CounterV2EnvelopeItem(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString(), metric = "screen_view_v2", segment = CounterV2Segment("global", "all"), dimensions = mapOf("screen" to "home"), n = 1),
                CounterV2EnvelopeItem(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString(), metric = "screen_view_v2", segment = CounterV2Segment("language", "fr"), dimensions = mapOf("screen" to "login"), n = 1),
                CounterV2EnvelopeItem(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString(), metric = "screen_view_v2", segment = CounterV2Segment("country", "ZZ"), dimensions = mapOf("screen" to "settings"), n = 1),
            ),
        )
        assertEquals(CounterV2Repository.Result.Invalid("invalid_segment"), repo.apply(pid, request, "a".repeat(32)))
        assertEquals(0, transaction(db) { CounterCellsV2.selectAll().count() })
    }

    @Test fun `daily and hourly cells remain separate`() {
        val (repo, db, pid) = setup()
        repo.apply(pid, request(batchId = "daily_0000000000001", hour = null), "a".repeat(32))
        repo.apply(pid, request(batchId = "hourly_000000000001", hour = 9), "b".repeat(32))
        assertEquals(setOf((-1).toShort(), 9.toShort()), transaction(db) { CounterCellsV2.selectAll().map { it[CounterCellsV2.hour] }.toSet() })
    }

    @Test fun `funnel witness can arrive after its pinned entry cohort day`() {
        val (repo, _, pid) = setup()
        val entry = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(3)
        val targetDims = mapOf("namespace" to "dashboard", "f" to "signup", "revision" to "1", "step" to "2", "entry_day" to entry.toString())
        val hash = (com.quietmetrix.server.counters.CounterRegistryV2.validate("funnel_step_v2", targetDims) as com.quietmetrix.server.counters.CounterRegistryV2.Result.Accepted).value.hash
        val request = CounterV2EnvelopeRequest(
            schema = 2, batchId = "funnelwitness_000001", sdk = com.quietmetrix.server.domain.SdkInfo("android", "2.0"),
            items = listOf(CounterV2EnvelopeItem(entry.plusDays(3).toString(), metric = "witness_window_v2",
                segment = CounterV2Segment("global", "all"), dimensions = mapOf(
                    "target_metric" to "funnel_step_v2", "target_hash" to hash, "days" to "1", "end_day" to entry.toString(),
                ), n = 1)),
        )
        assertTrue(repo.apply(pid, request, "z".repeat(32)) is CounterV2Repository.Result.Accepted)
    }

    @Test fun `fixed window breakdowns select one facet projection without counting the global row`() {
        val (repo, db, pid) = setup()
        transaction(db) { SchemaUtils.create(Counters) }
        val today = LocalDate.now(java.time.ZoneOffset.UTC)
        val dims = mapOf("scope_kind" to "project", "scope_key" to "all", "days" to "7", "end_day" to today.toString())
        repeat(5) { index ->
            val request = CounterV2EnvelopeRequest(
                schema = 2, batchId = "facetbatch_${index.toString().padStart(8, '0')}", sdk = SdkInfo("android", "2.0"),
                items = listOf(
                    CounterV2EnvelopeItem(today.toString(), metric = "unique_window_v2", segment = CounterV2Segment("global", "all"), dimensions = dims, n = 1),
                    CounterV2EnvelopeItem(today.toString(), metric = "unique_window_v2", segment = CounterV2Segment("platform", "android"), dimensions = dims, n = 1),
                    CounterV2EnvelopeItem(today.toString(), metric = "unique_window_v2", segment = CounterV2Segment("country", "FR"), dimensions = dims, n = 1),
                    CounterV2EnvelopeItem(today.toString(), metric = "unique_window_v2", segment = CounterV2Segment("device_class", "phone"), dimensions = dims, n = 1),
                ),
            )
            assertTrue(repo.apply(pid, request, index.toString().padStart(32, 'a')) is CounterV2Repository.Result.Accepted)
        }
        val counters = CounterRepository(db)
        assertEquals(listOf(CounterRepository.FacetTotal("android", 5L)), counters.uniqueWindowByFacet(pid, today.minusDays(6), today, 7, today, "platform"))
        assertEquals(listOf(CounterRepository.FacetTotal("FR", 5L)), counters.uniqueWindowByFacet(pid, today.minusDays(6), today, 7, today, "country"))
        assertEquals(listOf(CounterRepository.FacetTotal("phone", 5L)), counters.uniqueWindowByFacet(pid, today.minusDays(6), today, 7, today, "device_class"))
    }

    @Test fun `payload canonicalization matches the cross backend fixture`() {
        val (repo, _, _) = setup()
        val vector = CounterV2BatchRequest(
            batchId = "batch_0000000000001", day = "2026-10-04", hour = 9,
            sdk = com.quietmetrix.server.domain.SdkInfo("android", "2.0"),
            app = com.quietmetrix.server.domain.CounterAppInfo(version = "1.0"),
            counters = listOf(CounterV2Item("screen_view_v2", mapOf("screen" to "home"), 2, 1)),
        )
        assertEquals("48f8e44362b4c88c059f15a40db705df084fa9f2254804aaed291a26e8ca1fc7", repo.canonicalPayloadHash(vector))
    }

    @Test fun `concurrent retry claims apply one batch exactly once`() = runBlocking {
        val (repo, db, pid) = setup()
        val jobs = (0 until 20).map { index -> async(Dispatchers.IO) {
            repo.apply(pid, request(), index.toString().padStart(32, 'a'))
        } }
        val results = jobs.awaitAll()
        assertEquals(1, results.count { it == CounterV2Repository.Result.Accepted(1, false) })
        assertEquals(19, results.count { it == CounterV2Repository.Result.Accepted(1, true) })
        val row = transaction(db) { CounterCellsV2.selectAll().single() }
        assertEquals(3L, row[CounterCellsV2.n])
    }

    @Test fun `dimension cardinality cap rejects the whole additional batch`() {
        val (_, db, pid) = setup()
        val limited = CounterV2Repository(db, maxDistinctCellsPerMetric = 1)
        limited.apply(pid, request(batchId = "batch_first_000001"), "a".repeat(32))
        val invalid = limited.apply(pid, request(batchId = "batch_second_00001", counters = listOf(
            CounterV2Item("screen_view_v2", mapOf("screen" to "search"), 1, 1),
            CounterV2Item("event_v2", mapOf("name" to "open"), 1, 1),
        )), "b".repeat(32))
        assertEquals(CounterV2Repository.Result.Invalid("cardinality_cap"), invalid)
        assertEquals(1L, transaction(db) { CounterBatchReceiptsV2.selectAll().count() }, "Rejected batches must not create successful replay receipts")
        assertEquals(1L, transaction(db) { CounterCellsV2.selectAll().count() })
    }

    @Test fun `dashboard funnel query pins revision and gates by entry cohort witnesses`() {
        val (repo, db, pid) = setup()
        val day = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(2)
        transaction(db) {
            fun insert(index: Int, revision: Int, step: Int, contributors: Long = 1, namespace: String = "dashboard", entry: LocalDate = day) {
                CounterCellsV2.insert {
                    it[CounterCellsV2.projectId] = pid
                    it[CounterCellsV2.day] = day.plusDays(1)
                    it[CounterCellsV2.hour] = (-1).toShort()
                    it[CounterCellsV2.metric] = "funnel_step_v2"
                    it[CounterCellsV2.platform] = "android"
                    it[CounterCellsV2.sdkVersion] = "2.0"
                    it[CounterCellsV2.appVersion] = "app-$index"
                    it[CounterCellsV2.country] = "  "
                    it[CounterCellsV2.dimsHash] = index.toString().padStart(64, '0')
                    it[CounterCellsV2.dims] = """{"namespace":"$namespace","f":"signup","revision":"$revision","step":"$step","entry_day":"$entry"}"""
                    it[CounterCellsV2.n] = 1L
                    it[CounterCellsV2.contributors] = contributors
                }
            }
            repeat(5) { insert(it, 3, 1) }
            repeat(4) { insert(it + 10, 3, 2) }
            repeat(5) { insert(it + 20, 2, 2) }
        }

        val cells = repo.readDashboardFunnelSteps(pid, "signup", 3, day, day)
        assertEquals(listOf(com.quietmetrix.server.funnels.FunnelStepCell(1, 5L)), cells)
    }

    @Test fun `experiment report combines v2 platform partitions then gates and pins revision`() {
        val (repo, db, pid) = setup()
        val day = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(2)
        transaction(db) {
            fun insert(index: Int, metric: String, variant: String, revision: Int = 3, platform: String = "android", contributors: Long = 1) {
                CounterCellsV2.insert {
                    it[CounterCellsV2.projectId] = pid
                    it[CounterCellsV2.day] = day
                    it[CounterCellsV2.hour] = (-1).toShort()
                    it[CounterCellsV2.metric] = metric
                    it[CounterCellsV2.platform] = platform
                    it[CounterCellsV2.sdkVersion] = "2.0"
                    it[CounterCellsV2.appVersion] = "app-$index"
                    it[CounterCellsV2.country] = "  "
                    it[CounterCellsV2.dimsHash] = index.toString().padStart(64, '0')
                    it[CounterCellsV2.dims] = """{"exp":"signup","revision":"$revision","variant":"$variant"}"""
                    it[CounterCellsV2.n] = 1L
                    it[CounterCellsV2.contributors] = contributors
                }
            }
            repeat(2) { insert(it, "experiment_exposure_v2", "a", platform = "android") }
            repeat(3) { insert(it + 2, "experiment_exposure_v2", "a", platform = "ios") }
            repeat(4) { insert(it + 5, "experiment_exposure_v2", "b", platform = "android") }
            insert(9, "experiment_exposure_v2", "a", revision = 2)
        }
        val a = repo.readExperimentCells(pid, "experiment_exposure_v2", day, day, "signup", 3)
        assertEquals(listOf(CounterRepository.CounterCell(mapOf("exp" to "signup", "rev" to "3", "variant" to "a"), 5L, 5L)), a)
        val b = repo.readExperimentCells(pid, "experiment_exposure_v2", day, day, "signup", 3)
        assertEquals(1, b.size)
    }

    @Test fun `transition reads filter platform before k gate and report suppressed source presence`() {
        val (repo, db, pid) = setup()
        val day = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1)
        transaction(db) {
            fun insert(index: Int, platform: String, contributors: Long) {
                CounterCellsV2.insert {
                    it[CounterCellsV2.projectId] = pid; it[CounterCellsV2.day] = day
                    it[CounterCellsV2.hour] = (-1).toShort(); it[CounterCellsV2.metric] = "screen_transition_v2"
                    it[CounterCellsV2.platform] = platform; it[CounterCellsV2.sdkVersion] = "2.0"
                    it[CounterCellsV2.appVersion] = "app-$index"; it[CounterCellsV2.country] = "  "
                    it[CounterCellsV2.dimsHash] = index.toString().padStart(64, '0')
                    it[CounterCellsV2.dims] = """{"from":"home","to":"details"}"""
                    it[CounterCellsV2.n] = 1L; it[CounterCellsV2.contributors] = contributors
                }
            }
            repeat(5) { insert(it, "android", 1) }
            insert(10, "ios", 2)
        }
        val android = repo.readTransitions(pid, day, day, "android")
        assertTrue(android.present)
        assertEquals(5L, android.cells.single().n)
        val ios = repo.readTransitions(pid, day, day, "ios")
        assertTrue(ios.present)
        assertEquals(emptyList(), ios.cells)
        assertEquals(6L, repo.readTransitions(pid, day, day, "all").cells.single().n)
    }
}
