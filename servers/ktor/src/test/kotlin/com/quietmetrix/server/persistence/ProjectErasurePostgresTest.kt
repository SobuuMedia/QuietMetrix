package com.quietmetrix.server.persistence

import com.quietmetrix.server.ai.AiAccounting
import com.quietmetrix.server.ai.AiConfig
import com.quietmetrix.server.ai.AiDenied
import com.quietmetrix.server.ai.AiRates
import com.quietmetrix.server.ai.aiHash
import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.config.AuthConfig
import com.quietmetrix.server.config.DbConfig
import com.quietmetrix.server.config.RateLimitConfig
import com.quietmetrix.server.domain.CounterV2BatchRequest
import com.quietmetrix.server.domain.CounterV2Item
import com.quietmetrix.server.experiments.ExperimentDefinition
import com.quietmetrix.server.experiments.VariantSpec
import com.quietmetrix.server.funnels.FunnelDefinition
import com.quietmetrix.server.funnels.FunnelStepDefinition
import com.quietmetrix.server.maintenance.DataMaintenance
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.Database
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectErasurePostgresTest {
    @Test fun `migrated PostgreSQL erases live project records and cannot recreate AI results`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { pg ->
            pg.start()
            val source = PGSimpleDataSource().apply { setURL(pg.jdbcUrl); user = pg.username; password = pg.password }
            org.flywaydb.core.Flyway.configure().dataSource(source).locations("filesystem:migrations").load().migrate()
            val db = Database.connect(source)
            val users = UserRepository(db); val projects = ProjectRepository(db)
            kotlin.test.assertTrue(projects.ready(), "Migrated PostgreSQL must pass the real readiness probe")
            val owner = users.create("erase@example.com", "password123")
            val apiKey = projects.create("Erase me", null, owner)
            val id = projects.validateApiKey(apiKey)!!
            assertFailsWith<AccountOwnsProjects> { users.delete(owner) }
            assertFailsWith<ProjectLimitExceeded> { projects.create("Over quota", null, owner, projectLimit = 1) }
            val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
            try {
                val limiter = com.quietmetrix.server.ratelimit.SharedRateLimiter(db)
                val windowTime = System.currentTimeMillis() / 1000
                val attempts = (1..24).map { pool.submit<Boolean> { limiter.tryConsume("concurrent-fixture", 7, now = windowTime) } }
                assertEquals(7, attempts.count { it.get() }, "Shared limiter must coordinate parallel connections")
                val quotaOwner = users.create("quota@example.com", "password123")
                val creations = (1..12).map { index -> pool.submit<Boolean> {
                    try { projects.create("Quota $index", null, quotaOwner, projectLimit = 3); true }
                    catch (_: ProjectLimitExceeded) { false }
                } }
                assertEquals(3, creations.count { it.get() }, "Parallel creates must respect the organization limit")
                assertEquals(3L, projects.countByOwnerId(quotaOwner))
            } finally { pool.shutdownNow() }
            val today = LocalDate.now(ZoneOffset.UTC)
            val counters = CounterRepository(db)
            counters.upsertBatchOnce(id, "erasure_batch_00001", "a".repeat(64), "b".repeat(32), today,
                listOf(CounterRepository.CounterDelta("event", mapOf("name" to "open"), n=1, isNewDevice=true)))
            counters.upsert(id, today, CounterRepository.CounterDelta("invalid_metric", mapOf("private" to "redacted"), n=1, isNewDevice=true))
            assertIs<CounterV2Repository.Result.Accepted>(CounterV2Repository(db).apply(id,
                CounterV2BatchRequest("erasure_batch_v2_01", day=today.toString(), counters=listOf(CounterV2Item("event_v2",mapOf("name" to "open"),1,1))), "c".repeat(32)))
            FunnelRepository(db).create(id, FunnelDefinition("signup", "Signup", steps=listOf(FunnelStepDefinition("start","open"),FunnelStepDefinition("end","submit"))))
            ExperimentRepository(db).create(id, ExperimentDefinition("banner","Banner",null,100,listOf(VariantSpec("a",50),VariantSpec("b",50)),null))
            ProjectMemberRepository(db).add(id,owner,"viewer")
            val ai = AiAccounting(source,AiConfig(model="fixture",apiKey="fixture",rates=AiRates(100000,200000,50000)),false,requireProject=true)
            val op = aiHash("erasure-operation"); val cache = aiHash("erasure-cache")
            ai.reserve(owner.toString(),id.toString(),op,"fixture",cache)
            assertTrue(projects.softDelete(id)); assertFalse(projects.softDelete(id))
            assertNull(projects.validateApiKey(apiKey))
            ai.finish(op,buildJsonObject { put("answer","late response") },1,cache)
            assertEquals("project_unavailable",assertFailsWith<AiDenied> { ai.reserve(owner.toString(),id.toString(),aiHash("late"),"late") }.code)
            source.connection.use { connection ->
                for(table in listOf("projects","counters","counters_quarantine","counter_batch_receipts","counter_cells_v2","counter_batch_receipts_v2","funnels","funnel_definition_revisions","experiments","project_members")) {
                    val column = if(table == "projects") "id" else "project_id"
                    connection.prepareStatement("SELECT COUNT(*) FROM $table WHERE $column=?").use { q ->
                        q.setLong(1,id); q.executeQuery().use { rows -> rows.next(); assertEquals(0,rows.getInt(1),table) }
                    }
                }
                for(table in listOf("ai_operations","ai_summary_cache")) connection.createStatement().use { q ->
                    q.executeQuery("SELECT COUNT(*) FROM $table").use { rows -> rows.next(); assertEquals(0,rows.getInt(1),table) }
                }
            }
            val config=AppConfig(AppConfig.Profile.SELFHOST,db=DbConfig("","","","",1),auth=AuthConfig("secret","qm","qm",2),rateLimit=RateLimitConfig(false,10,60))
            DataMaintenance(db,config).run()
            assertTrue(users.delete(owner))
        }
    }
}
