package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProjectActivitySummaryTest {
    @Test
    fun `summary uses ingest metadata and hides platform coverage below five contributors`() {
        val db = Database.connect("jdbc:h2:mem:project_summary;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, Counters, CounterCellsV2) }
        val ids: List<Long> = transaction(db) {
            val owner: Long = Users.insert { it[email] = "summary@example.test"; it[passwordHash] = "unused" }[Users.id]
            fun project(name: String): Long = Projects.insert {
                it[Projects.name] = name
                it[Projects.ownerUserId] = owner
                it[Projects.apiKeyHash] = "hash"
                it[Projects.apiKeyLast4] = "test"
            }[Projects.id]
            val complete = project("Complete")
            val small = project("Small")
            val dims = """{"days":"7","end_day":"${LocalDate.now(java.time.ZoneOffset.UTC)}","scope_key":"all","scope_kind":"project"}"""
            val platforms = listOf("android", "ios", "web", "desktop", "unknown")
            (listOf(complete, small) zip listOf(5, 4)).forEach { pair ->
                val projectId = pair.first
                val count = pair.second
                platforms.take(count).forEachIndexed { index, platform ->
                    CounterCellsV2.insert {
                        it[CounterCellsV2.projectId] = projectId
                        it[CounterCellsV2.day] = LocalDate.now(java.time.ZoneOffset.UTC)
                        it[CounterCellsV2.hour] = (-1).toShort()
                        it[CounterCellsV2.metric] = "unique_window_v2"
                        it[CounterCellsV2.platform] = platform
                        it[CounterCellsV2.sdkVersion] = if (index == 0) "1.2.3" else "unknown"
                        it[CounterCellsV2.appVersion] = ""
                        it[CounterCellsV2.country] = "  "
                        it[CounterCellsV2.dimsHash] = ("%064x".format(index + 1))
                        it[CounterCellsV2.dims] = dims
                        it[CounterCellsV2.n] = 1
                        it[CounterCellsV2.contributors] = 1
                        it[CounterCellsV2.updatedAt] = LocalDateTime.now()
                    }
                }
            }
            listOf(complete, small)
        }

        val result = ProjectRepository(db).activitySummaries(ids)
        assertEquals(listOf("android", "desktop", "ios", "web"), result.getValue(ids[0]).platforms)
        assertEquals(listOf("1.2.3"), result.getValue(ids[0]).sdkVersions)
        assertNotNull(result.getValue(ids[0]).lastReceivedAt)
        assertEquals("receiving_data", result.getValue(ids[0]).status)
        assertEquals(emptyList(), result.getValue(ids[1]).platforms)
        assertEquals(emptyList(), result.getValue(ids[1]).sdkVersions)
    }
}
