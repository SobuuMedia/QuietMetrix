package com.quietmetrix.server.ai

import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real PostgreSQL account locking proves multi-process-equivalent allowance enforcement. */
class AiAccountingPostgresTest {
    @Test fun `shared monthly allowance concurrency retries billing and calendar boundaries`() {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { pg ->
            pg.start()
            val source=PGSimpleDataSource().apply { setURL(pg.jdbcUrl); user=pg.username; password=pg.password }
            org.flywaydb.core.Flyway.configure().dataSource(source).locations("filesystem:migrations").load().migrate()
            val config=AiConfig(model="fixture",apiKey="fixture",rates=AiRates(100000,200000,50000))
            val ledger=AiAccounting(source,config,true)
            fun plan(id: String, count: Long, cost: Long=1000000000) { ledger.operator("plans",id,buildJsonObject { put("ai_enabled",true);put("generation_limit",count);put("cost_limit",cost) }) }
            fun assign(account: String, id: String, expiry: Long?=null) { ledger.operator("assignments",account,buildJsonObject { put("plan_id",id);put("active",true);if(expiry!=null)put("expires_at",expiry) }) }
            fun usage(account: String) = ledger.operator("usage",account,null).jsonArray.last().jsonObject
            plan("small",3);assign("owner","small")
            val pool=Executors.newFixedThreadPool(12)
            try {
                val results=pool.invokeAll((0 until 30).map { i -> Callable {
                    runCatching { ledger.reserve("owner","project-${i%3}",aiHash("op$i"),aiHash("fp$i")) }.isSuccess
                } }).count { it.get() }
                assertEquals(3,results)
            } finally { pool.shutdownNow() }
            assertEquals(3,usage("owner")["generations"]!!.jsonPrimitive.int)
            assertEquals("ai_allowance_exhausted",ledger.capabilities("owner")["reason"]!!.jsonPrimitive.content)
            plan("larger",10);assign("owner","larger")
            assertTrue(ledger.capabilities("owner")["available"]!!.jsonPrimitive.boolean)
            assertEquals(3,usage("owner")["generations"]!!.jsonPrimitive.int)
            val id=aiHash("retry")
            ledger.reserve("owner","p",id,"same")
            assertEquals("ai_operation_pending",assertFailsWith<AiDenied> { ledger.reserve("owner","p",id,"same") }.code)
            val answer=buildJsonObject { put("answer","observed");put("sources",buildJsonArray { add("sessions") }) }
            ledger.finish(id,answer,15)
            assertEquals(answer,ledger.reserve("owner","p",id,"same").cached)
            assertEquals("ai_operation_conflict",assertFailsWith<AiDenied> { ledger.reserve("owner","p",id,"different") }.code)
            val failed=aiHash("failed")
            ledger.reserve("owner","p",failed,"failure")
            ledger.finish(failed,null,42)
            assertEquals(4,usage("owner")["generations"]!!.jsonPrimitive.int)
            val unknown=aiHash("unknown")
            val before=usage("owner")["cost"]!!.jsonPrimitive.long
            ledger.reserve("owner","p",unknown,"unknown")
            ledger.finish(unknown,null,null)
            assertEquals(before+config.rates!!.reservation,usage("owner")["cost"]!!.jsonPrimitive.long)
            val cached=aiHash("cached")
            ledger.reserve("owner","p",cached,"summary","cache-key")
            ledger.finish(cached,answer,0,"cache-key")
            assign("owner","small")
            assertEquals(answer,ledger.cached("owner","cache-key"))
            assertFalse(ledger.capabilities("owner")["available"]!!.jsonPrimitive.boolean)
            assign("owner","larger",Instant.now().epochSecond-1)
            assertFalse(ledger.capabilities("owner")["enabled"]!!.jsonPrimitive.boolean)
            assertNull(ledger.cached("owner","cache-key"))
            assertFailsWith<AiDenied> { ledger.reserve("unassigned","p",aiHash("no"),"no") }
            plan("cost-only",100,config.rates!!.reservation-1);assign("ceiling","cost-only")
            assertEquals("ai_allowance_exhausted",assertFailsWith<AiDenied> { ledger.reserve("ceiling","p",aiHash("cost"),"cost") }.code)
            plan("month",1);assign("calendar","month")
            ledger.reserve("calendar","p",aiHash("dec"),"dec",now=Instant.parse("2026-12-31T23:59:59Z"))
            ledger.reserve("calendar","p",aiHash("jan"),"jan",now=Instant.parse("2027-01-01T00:00:00Z"))
            ledger.finish(aiHash("dec"),null,7)
            val months=ledger.operator("usage","calendar",null).jsonArray.map { it.jsonObject }.associateBy { it["month"]!!.jsonPrimitive.content }
            assertEquals(0,months["2026-12-01"]!!["generations"]!!.jsonPrimitive.int)
            assertEquals(1,months["2027-01-01"]!!["generations"]!!.jsonPrimitive.int)
            plan("recovery",5);assign("recover","recovery")
            val old=Instant.now().minusSeconds(180)
            ledger.reserve("recover","p",aiHash("old"),"old",now=old)
            ledger.reserve("recover","p",aiHash("new"),"new")
            assertEquals(1,usage("recover")["generations"]!!.jsonPrimitive.int)
            assertEquals(2*config.rates!!.reservation,usage("recover")["cost"]!!.jsonPrimitive.long)
            plan("minute",1000);assign("minute","minute")
            repeat(20) { i -> ledger.reserve("minute","p",aiHash("minute$i"),"$i") }
            assertEquals("ai_rate_limited",assertFailsWith<AiDenied> { ledger.reserve("minute","p",aiHash("minute21"),"21") }.code)
            plan("cost-concurrent",1000,3*config.rates!!.reservation);assign("cost-concurrent","cost-concurrent")
            val costPool=Executors.newFixedThreadPool(12)
            try {
                val results=costPool.invokeAll((0 until 24).map { i -> Callable { runCatching { ledger.reserve("cost-concurrent","p${i%3}",aiHash("cost-op$i"),"$i") }.isSuccess } }).count { it.get() }
                assertEquals(3,results)
            } finally { costPool.shutdownNow() }
            assign("boundary-minute","minute")
            val boundaryPool=Executors.newFixedThreadPool(12)
            try {
                val boundary=Instant.parse("2026-10-31T23:59:59Z")
                val results=boundaryPool.invokeAll((0 until 30).map { i -> Callable { runCatching { ledger.reserve("boundary-minute","p${i%3}",aiHash("boundary-op$i"),"$i",now=boundary.plusSeconds((i%2).toLong())) }.isSuccess } }).count { it.get() }
                assertEquals(20,results)
            } finally { boundaryPool.shutdownNow() }
            plan("lease",1000);assign("lease","lease")
            val lease=aiHash("lease1")
            ledger.reserve("lease","p",lease,"f1","summary-lease")
            assertEquals("ai_operation_pending",assertFailsWith<AiDenied> { ledger.reserve("lease","p",aiHash("lease2"),"f2","summary-lease") }.code)
            ledger.finish(lease,answer,0,"summary-lease")
            assertEquals(answer,ledger.reserve("lease","p",aiHash("lease2"),"f2","summary-lease").cached)
            assertEquals(1,ledger.operator("usage","lease",null).jsonArray.single().jsonObject["generations"]!!.jsonPrimitive.int)
            ledger.cleanup(Instant.now().plusSeconds(901))
            source.connection.use { c -> c.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM ai_operations WHERE result IS NOT NULL").use { rows -> rows.next();assertEquals(0,rows.getInt(1)) } } }
            val self=AiAccounting(source,config,false)
            repeat(25) { self.reserve("self","p",aiHash("self$it"),"$it") }
            assertEquals(25,self.operator("usage","self",null).jsonArray.single().jsonObject["generations"]!!.jsonPrimitive.int)
        }
    }
}
