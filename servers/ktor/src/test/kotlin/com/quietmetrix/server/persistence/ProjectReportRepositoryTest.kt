package com.quietmetrix.server.persistence

import com.quietmetrix.server.counters.CounterRegistryV2
import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectReportRepositoryTest {
    private fun setup(): Triple<ProjectReportRepository, Database, Long> {
        val db = Database.connect("jdbc:h2:mem:projectreport_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, Counters, CounterCellsV2) }
        UserRepository(db).create("report@example.com", "password123")
        ProjectRepository(db).create("report", null, 1L)
        return Triple(ProjectReportRepository(db), db, 1L)
    }

    @Test fun `country and screen filters select independent exact populations`() {
        val (repo,db,pid)=setup()
        val today=LocalDate.now(ZoneOffset.UTC)
        transaction(db) {
            for ((kind,value,count,scope,key) in listOf(
                listOf("global","all","20","project","all"),listOf("country","DE","6","project","all"),
                listOf("country","FR","3","project","all"),listOf("global","all","7","screen","home"))) {
                val dims=mapOf("days" to "7","end_day" to today.toString(),"scope_kind" to scope,"scope_key" to key)
                val valid=(CounterRegistryV2.validate("unique_window_v2",dims) as CounterRegistryV2.Result.Accepted).value
                CounterCellsV2.insert {
                    it[projectId]=pid; it[day]=today; it[hour]=(-1).toShort(); it[metric]="unique_window_v2"
                    it[platform]="android"; it[appVersion]=""; it[country]=""; it[segmentKind]=kind; it[segmentValue]=value
                    it[dimsHash]=valid.hash; it[CounterCellsV2.dims]=kotlinx.serialization.json.Json.encodeToString(dims); it[n]=count.toLong(); it[contributors]=count.toLong()
                }
            }
        }
        assertEquals(20L,repo.read(pid,"7d",today,"all").overview.getValue("people").value)
        val germany=repo.read(pid,"7d",today,"all",filterKind="country",filterValue="DE")
        assertEquals(6L,germany.overview.getValue("people").value)
        assertEquals("suppressed",repo.read(pid,"7d",today,"all",filterKind="country",filterValue="FR").overview.getValue("people").state)
        val home=repo.read(pid,"7d",today,"all",filterKind="screen",filterValue="home")
        assertEquals(7L,home.overview.getValue("people").value)
        assertEquals(null,home.overview.getValue("visits").value)
    }

    @Test fun `ordinary groups are disclosed only with their matching fixed-window witness`() {
        val (repo, db, projectId) = setup()
        val today = LocalDate.now(ZoneOffset.UTC)
        val eventDims = mapOf("name" to "secret_action")
        val targetHash = (CounterRegistryV2.validate("event_v2", eventDims) as CounterRegistryV2.Result.Accepted).value.hash
        transaction(db) {
            repeat(5) { index -> CounterCellsV2.insert {
                it[CounterCellsV2.projectId] = projectId; it[CounterCellsV2.day] = today.minusDays(index.toLong())
                it[CounterCellsV2.hour] = (-1).toShort(); it[CounterCellsV2.metric] = "unique_window_v2"
                it[CounterCellsV2.platform] = "android"; it[CounterCellsV2.appVersion] = ""; it[CounterCellsV2.country] = ""
                it[CounterCellsV2.dimsHash] = index.toString().padStart(64, '0')
                it[CounterCellsV2.dims] = """{"days":"7","end_day":"$today","scope_key":"all","scope_kind":"project"}"""
                it[CounterCellsV2.n] = 1; it[CounterCellsV2.contributors] = 1
            } }
            CounterCellsV2.insert {
                it[CounterCellsV2.projectId] = projectId; it[CounterCellsV2.day] = today
                it[CounterCellsV2.hour] = (-1).toShort(); it[CounterCellsV2.metric] = "event_v2"
                it[CounterCellsV2.platform] = "android"; it[CounterCellsV2.appVersion] = ""; it[CounterCellsV2.country] = ""
                it[CounterCellsV2.dimsHash] = "a".repeat(64); it[CounterCellsV2.dims] = """{"name":"secret_action"}"""
                it[CounterCellsV2.n] = 100; it[CounterCellsV2.contributors] = 0
            }
            val witnessDims = """{"days":"7","end_day":"$today","target_hash":"$targetHash","target_metric":"event_v2"}"""
            CounterCellsV2.insert {
                it[CounterCellsV2.projectId] = projectId; it[CounterCellsV2.day] = today
                it[CounterCellsV2.hour] = (-1).toShort(); it[CounterCellsV2.metric] = "witness_window_v2"
                it[CounterCellsV2.platform] = "android"; it[CounterCellsV2.appVersion] = ""; it[CounterCellsV2.country] = ""
                it[CounterCellsV2.dimsHash] = "b".repeat(64); it[CounterCellsV2.dims] = witnessDims
                it[CounterCellsV2.n] = 5; it[CounterCellsV2.contributors] = 0
            }
        }
        val report = repo.read(projectId, "7d", today, "all")
        assertEquals(100L, report.overview.getValue("events").value)
        assertEquals("reported", report.overview.getValue("events").state)
    }

    @Test fun `people reads exact seven day window and ignores daily people values`() {
        val (repo, db, projectId) = setup()
        val today = LocalDate.now(ZoneOffset.UTC)
        transaction(db) {
            repeat(5) { index ->
                val origin = today.minusDays(index.toLong())
                CounterCellsV2.insert {
                    it[CounterCellsV2.projectId] = projectId
                    it[CounterCellsV2.day] = origin
                    it[CounterCellsV2.hour] = (-1).toShort()
                    it[CounterCellsV2.metric] = "unique_window_v2"
                    it[CounterCellsV2.platform] = "android"
                    it[CounterCellsV2.appVersion] = ""
                    it[CounterCellsV2.country] = "  "
                    it[CounterCellsV2.dimsHash] = index.toString().padStart(64, '0')
                    it[CounterCellsV2.dims] = """{"days":"7","end_day":"$today","scope_key":"all","scope_kind":"project"}"""
                    it[CounterCellsV2.n] = 1L
                    it[CounterCellsV2.contributors] = 1L
                }
            }
            CounterCellsV2.insert {
                it[CounterCellsV2.projectId] = projectId
                it[CounterCellsV2.day] = today
                it[CounterCellsV2.hour] = (-1).toShort()
                it[CounterCellsV2.metric] = "unique_window_v2"
                it[CounterCellsV2.platform] = "android"
                it[CounterCellsV2.appVersion] = ""
                it[CounterCellsV2.country] = "  "
                it[CounterCellsV2.dimsHash] = "f".repeat(64)
                it[CounterCellsV2.dims] = """{"days":"1","end_day":"$today","scope_key":"all","scope_kind":"project"}"""
                it[CounterCellsV2.n] = 99L
                it[CounterCellsV2.contributors] = 99L
            }
            fun activity(metric: String, dims: String = "{}", count: Long, hash: Char, segment: String = "global") {
                CounterCellsV2.insert {
                    it[CounterCellsV2.projectId] = projectId
                    it[CounterCellsV2.day] = today
                    it[CounterCellsV2.hour] = (-1).toShort()
                    it[CounterCellsV2.metric] = metric
                    it[CounterCellsV2.platform] = "android"
                    it[CounterCellsV2.appVersion] = ""
                    it[CounterCellsV2.country] = "  "
                    it[CounterCellsV2.dimsHash] = hash.toString().repeat(64)
                    it[CounterCellsV2.dims] = dims
                    it[CounterCellsV2.n] = count
                    it[CounterCellsV2.contributors] = 5L
                    it[CounterCellsV2.segmentKind] = segment
                    it[CounterCellsV2.segmentValue] = if (segment == "global") "all" else "android"
                }
            }
            activity("visit_start_v2", count = 8, hash = 'a')
            activity("event_v2", """{"name":"open"}""", 12, 'b')
            activity("event_v2", """{"name":"open"}""", 12, 'b', "platform")
            activity("visit_duration_ms_v2", count = 300_000, hash = 'c')
            activity("visit_complete_v2", count = 5, hash = 'd')
        }
        val report = repo.read(projectId, "7d", today, "all")
        assertEquals(5L, report.overview.getValue("people").value)
        assertEquals("reported", report.overview.getValue("people").state)
        assertEquals("v2", report.coverage.source)
        assertEquals("daily", report.coverage.resolution)
        assertEquals(7, report.charts.getValue("people").size)
        assertEquals(99L, report.charts.getValue("people").last().measures.getValue("people").value)
        assertEquals("insufficient_data", report.charts.getValue("people").first().measures.getValue("people").state)
        assertEquals(8L, report.overview.getValue("visits").value)
        assertEquals(12L, report.overview.getValue("events").value)
        assertEquals(60_000L, report.overview.getValue("visit_duration").value)
        assertEquals("reported", report.overview.getValue("visit_duration").state)
        assertEquals(8L, report.charts.getValue("visits").last().measures.getValue("visits").value)
        assertEquals(12L, report.charts.getValue("events").last().measures.getValue("events").value)
        assertEquals(60_000L, report.charts.getValue("visit_duration").last().measures.getValue("visit_duration").value)
        val platformReport = repo.read(projectId, "7d", today, "android")
        assertEquals(12L, platformReport.overview.getValue("events").value)
    }

    @Test fun `today People chart uses independently gated UTC-hour installation cells`() {
        val (repo, db, projectId) = setup()
        val today = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        transaction(db) {
            CounterCellsV2.insert {
                it[CounterCellsV2.projectId] = projectId
                it[CounterCellsV2.day] = today
                it[CounterCellsV2.hour] = 9
                it[CounterCellsV2.metric] = "unique_hour_v2"
                it[CounterCellsV2.platform] = "android"
                it[CounterCellsV2.appVersion] = ""
                it[CounterCellsV2.country] = "  "
                it[CounterCellsV2.dimsHash] = "0".repeat(64)
                it[CounterCellsV2.dims] = """{"scope_key":"all","scope_kind":"project"}"""
                it[CounterCellsV2.n] = 5L
                it[CounterCellsV2.contributors] = 5L
            }
        }
        val todayReport = repo.read(projectId, "today", today, "all", compare = true)
        val bins = todayReport.charts.getValue("people")
        assertEquals(true, bins.size in 1..24)
        assertEquals(bins.indices.toList(), bins.mapNotNull { it.hour })
        assertEquals(9, bins[9].hour)
        assertEquals(5L, bins[9].measures.getValue("people").value)
        assertEquals("hourly", todayReport.coverage.resolution)
        assertEquals("insufficient_data", bins[8].measures.getValue("people").state)
        assertEquals(24, todayReport.comparison_charts.getValue("people").size)
        assertEquals(9, todayReport.comparison_charts.getValue("people")[9].hour)
        assertEquals("unsupported", todayReport.comparison.getValue("people").state)
    }

    @Test fun `ninety day People chart ends with a shorter exact window bin`() {
        val (repo, _, projectId) = setup()
        val today = LocalDate.now(ZoneOffset.UTC)
        val bins = repo.read(projectId, "90d", today, "all").charts.getValue("people")
        assertEquals(13, bins.size)
        assertEquals(7L, java.time.temporal.ChronoUnit.DAYS.between(
            LocalDate.parse(bins.first().start_day), LocalDate.parse(bins.first().end_day),
        ) + 1)
        assertEquals(6, java.time.temporal.ChronoUnit.DAYS.between(
            LocalDate.parse(bins.last().start_day), LocalDate.parse(bins.last().end_day),
        ).toInt() + 1)
    }

    @Test fun `people below five witnesses is suppressed rather than zero`() {
        val (repo, db, projectId) = setup()
        val today = LocalDate.now(ZoneOffset.UTC)
        transaction(db) {
            repeat(4) { index ->
                val day = today.minusDays(index.toLong())
                CounterCellsV2.insert {
                    it[CounterCellsV2.projectId] = projectId
                    it[CounterCellsV2.day] = day
                    it[CounterCellsV2.hour] = (-1).toShort()
                    it[CounterCellsV2.metric] = "unique_window_v2"
                    it[CounterCellsV2.platform] = "android"
                    it[CounterCellsV2.appVersion] = ""
                    it[CounterCellsV2.country] = "  "
                    it[CounterCellsV2.dimsHash] = index.toString().padStart(64, '0')
                    it[CounterCellsV2.dims] = """{"days":"7","end_day":"$today","scope_key":"all","scope_kind":"project"}"""
                    it[CounterCellsV2.n] = 1L
                    it[CounterCellsV2.contributors] = 1L
                }
            }
        }
        val people = repo.read(projectId, "7d", today, "all").overview.getValue("people")
        assertEquals(null, people.value)
        assertEquals("suppressed", people.state)
    }

    @Test fun `comparison reads the adjacent exact fixed window`() {
        val (repo, db, projectId) = setup()
        val today = LocalDate.now(ZoneOffset.UTC)
        val previousEnd = today.minusDays(7)
        transaction(db) {
            for ((end, hashOffset) in listOf(today to 0, previousEnd to 10)) {
                repeat(5) { index ->
                    val day = end.minusDays(index.toLong())
                    CounterCellsV2.insert {
                        it[CounterCellsV2.projectId] = projectId
                        it[CounterCellsV2.day] = day
                        it[CounterCellsV2.hour] = (-1).toShort()
                        it[CounterCellsV2.metric] = "unique_window_v2"
                        it[CounterCellsV2.platform] = "android"
                        it[CounterCellsV2.appVersion] = ""
                        it[CounterCellsV2.country] = "  "
                        it[CounterCellsV2.dimsHash] = (hashOffset + index).toString().padStart(64, '0')
                        it[CounterCellsV2.dims] = """{"days":"7","end_day":"$end","scope_key":"all","scope_kind":"project"}"""
                        it[CounterCellsV2.n] = 1L
                        it[CounterCellsV2.contributors] = 1L
                    }
                }
            }
        }
        val headlineOnly = repo.read(projectId, "7d", today, "all", compare = false)
        assertEquals(5L, headlineOnly.comparison.getValue("people").value)
        assertTrue(headlineOnly.comparison_charts.isEmpty())
        assertTrue(headlineOnly.comparison_retention_cohorts.isEmpty())
        assertEquals("false", headlineOnly.scope["compare"])

        val report = repo.read(projectId, "7d", today, "all", compare = true)
        assertEquals(5L, report.overview.getValue("people").value)
        assertEquals(5L, report.comparison.getValue("people").value)
        assertEquals(7, report.comparison_charts.getValue("people").size)
        assertEquals(today.minusDays(13).toString(), report.comparison_charts.getValue("people").first().start_day)
    }

    @Test fun `retention report labels immature cohorts and gates mature cohort rates`() {
        val (repo, db, projectId) = setup()
        val today = LocalDate.now(ZoneOffset.UTC)
        val entryDay = today.minusDays(10)
        transaction(db) {
            fun add(metric: String, day: LocalDate, dims: String, index: Int, n: Long = 1L) {
                CounterCellsV2.insert {
                    it[CounterCellsV2.projectId] = projectId
                    it[CounterCellsV2.day] = day
                    it[CounterCellsV2.hour] = (-1).toShort()
                    it[CounterCellsV2.metric] = metric
                    it[CounterCellsV2.platform] = "android"
                    it[CounterCellsV2.sdkVersion] = "2.0"
                    it[CounterCellsV2.appVersion] = "app-$index"
                    it[CounterCellsV2.country] = "  "
                    it[CounterCellsV2.dimsHash] = index.toString().padStart(64, '0')
                    it[CounterCellsV2.dims] = dims
                    it[CounterCellsV2.n] = n
                    it[CounterCellsV2.contributors] = 1L
                }
            }
            repeat(5) { add("retention_entry_v2", entryDay, """{"entry_day":"$entryDay"}""", it) }
            repeat(5) { add("retention_return_v2", entryDay.plusDays(7), """{"entry_day":"$entryDay","age_day":"7"}""", it + 10) }
            val previousEntry = today.minusDays(40)
            repeat(5) { add("retention_entry_v2", previousEntry, """{"entry_day":"$previousEntry"}""", it + 40) }
            repeat(5) { add("retention_return_v2", previousEntry.plusDays(7), """{"entry_day":"$previousEntry","age_day":"7"}""", it + 50) }
            repeat(5) { add("screen_view_v2", today, """{"screen":"Home"}""", it + 20) }
            repeat(4) { add("screen_view_v2", today, """{"screen":"Rare"}""", it + 30) }
        }
        val report = repo.read(projectId, "30d", today, "all", compare = true)
        assertEquals("zero", report.retention.getValue("day_1").state)
        assertEquals(0.0, report.retention.getValue("day_1").rate)
        assertEquals(1.0, report.retention.getValue("day_7").rate)
        assertEquals("not_mature", report.retention.getValue("day_14").state)
        assertEquals(5L, report.retention_cohorts.single().size.value)
        assertEquals(1.0, report.retention_cohorts.single().days.getValue("7").rate)
        assertEquals("not_mature", report.retention_cohorts.single().days.getValue("14").state)
        assertEquals(listOf("Home"), report.definitions.getValue("screens"))
        assertEquals(today.minusDays(40).toString(), report.comparison_retention_cohorts.single().entry_day)
        assertEquals(1.0, report.comparison_retention_cohorts.single().days.getValue("7").rate)
    }
}
