package com.quietmetrix.server.maintenance

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.config.AuthConfig
import com.quietmetrix.server.config.DbConfig
import com.quietmetrix.server.config.RateLimitConfig
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.persistence.UserRepository
import com.quietmetrix.server.persistence.tables.AuthSessions
import com.quietmetrix.server.persistence.tables.CounterBatchReceipts
import com.quietmetrix.server.persistence.tables.CounterBatchReceiptsV2
import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.CountersQuarantine
import com.quietmetrix.server.persistence.tables.EventCountsDaily
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.RequestRateBuckets
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataMaintenanceTest {
    @Test fun `retention preserves recent data removes expired data and is repeatable`() {
        val db = Database.connect("jdbc:h2:mem:maintenance;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        transaction(db) { SchemaUtils.create(Users, Projects, AuthSessions, RequestRateBuckets, Counters, CountersQuarantine, CounterCellsV2, CounterBatchReceipts, CounterBatchReceiptsV2, EventCountsDaily) }
        val user = UserRepository(db).create("retention@example.com", "password1")
        val projects = ProjectRepository(db)
        projects.create("Retention", null, user)
        val id = (projects.findByOwnerId(user).single()["id"] as Number).toLong()
        val today = LocalDate.of(2026, 10, 7)
        transaction(db) {
            for (date in listOf(today, today.minusDays(30))) Counters.insert {
                it[projectId] = id; it[day] = date; it[metric] = "event"; it[platform] = "web"
                it[appVersion] = ""; it[country] = "  "; it[dimsHash] = "a".repeat(64); it[dims] = "{\"name\":\"login\"}"
                it[n] = 5; it[devices] = 5
            }
            CountersQuarantine.insert {
                it[projectId] = id; it[payload] = "private@example.com"; it[quarantineReason] = "invalid"; it[quarantineDetail] = "private@example.com"
            }
            for ((index, days) in listOf(7, 90).withIndex()) {
                CounterCellsV2.insert {
                    it[projectId] = id; it[day] = today; it[metric] = "unique_window_v2"
                    it[dimsHash] = (index + 1).toString().repeat(64)
                    it[dims] = "{\"end_day\":\"$today\",\"days\":$days}"
                    it[n] = 5; it[contributors] = 5
                }
                Counters.insert {
                    it[projectId] = id; it[day] = today; it[metric] = "unique_window_v2"
                    it[dimsHash] = (index + 1).toString().repeat(64)
                    it[dims] = "{\"end_day\":\"$today\",\"days\":$days}"
                    it[n] = 5; it[devices] = 5
                }
            }
        }
        val config = AppConfig(AppConfig.Profile.CLOUD, db = DbConfig("", "", "", "", 1), auth = AuthConfig("secret", "qm", "qm", 2), rateLimit = RateLimitConfig(false, 10, 60))
        val maintenance = DataMaintenance(db, config)
        assertEquals(3, maintenance.run(today))
        assertEquals(0, maintenance.run(today))
        transaction(db) {
            assertEquals(2, Counters.selectAll().count())
            assertTrue(Counters.selectAll().all { it[Counters.day] == today })
            assertEquals("1".repeat(64), CounterCellsV2.selectAll().single()[CounterCellsV2.dimsHash])
            assertEquals("{}", CountersQuarantine.selectAll().single()[CountersQuarantine.payload])
            assertNull(CountersQuarantine.selectAll().single()[CountersQuarantine.quarantineDetail])
        }
    }
}
