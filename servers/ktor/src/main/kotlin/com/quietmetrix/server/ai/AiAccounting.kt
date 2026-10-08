package com.quietmetrix.server.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.sql.Connection
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

/** Public capabilities never expose consumption or provider details. */
fun aiCapabilities(enabled: Boolean, available: Boolean, reason: String? = null, reset: String): JsonObject = buildJsonObject {
    put("enabled", enabled); put("available", available); if(reason != null) put("reason", reason); put("reset_date", reset)
}

/** Operation keys bind account, project and request context with SHA-256. */
fun aiHash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

/** Service errors contain only public codes. */
class AiDenied(val code: String) : RuntimeException(code)

/** A reserved operation or a short-lived deduplicated result. */
data class AiReservation(val id: String, val cached: JsonObject? = null)

/** Durable allowance enforcement serializes generation decisions on the account/month row. */
class AiAccounting(private val source: DataSource, private val config: AiConfig, private val cloud: Boolean, private val requireProject: Boolean = false) {
    private val json = Json
    /** Runs all ledger mutations atomically; database failures fail closed. */
    private fun <T> tx(block: (Connection) -> T): T = source.connection.use { c ->
        c.autoCommit = false
        try { val result = block(c); c.commit(); result } catch(e: Exception) { c.rollback(); throw e }
    }
    /** Executes a parameterized mutation. */
    private fun Connection.update(sql: String, vararg args: Any?): Int = prepareStatement(sql).use { p -> args.forEachIndexed { i,v -> p.setObject(i+1,v) }; p.executeUpdate() }
    /** Reads a parameterized result as operator-only JSON. */
    private fun Connection.rows(sql: String, vararg args: Any?): List<JsonObject> = prepareStatement(sql).use { p ->
        args.forEachIndexed { i,v -> p.setObject(i+1,v) }
        p.executeQuery().use { r -> buildList { while(r.next()) add(buildJsonObject { for(i in 1..r.metaData.columnCount) { val v = r.getObject(i); put(r.metaData.getColumnLabel(i).lowercase(), when(v) { null -> JsonNull; is Boolean -> JsonPrimitive(v); is Number -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }) } }) } }
    }
    /** Inserts a singleton lock row without replacing existing usage. */
    private fun Connection.insertOnce(table: String, columns: String, placeholders: String, vararg args: Any?) {
        val sql = "INSERT INTO $table ($columns) VALUES ($placeholders) ON CONFLICT DO NOTHING"
        update(sql, *args)
    }
    /** Reads current assignment after acquiring the account lock when requested. */
    private fun entitlement(c: Connection, account: String, now: Long): JsonObject? {
        if(!cloud) return buildJsonObject { put("generation_limit", Long.MAX_VALUE); put("cost_limit", Long.MAX_VALUE) }
        return c.rows("SELECT p.* FROM ai_assignments a JOIN ai_plans p ON p.plan_id=a.plan_id WHERE a.account_id=? AND a.active=TRUE AND p.ai_enabled=TRUE AND (a.expires_at IS NULL OR a.expires_at>?)", account, now).firstOrNull()
    }
    /** Returns configuration and entitlement without any counters. */
    fun capabilities(account: String, now: Instant = Instant.now()): JsonObject {
        val date = now.atZone(ZoneOffset.UTC).toLocalDate()
        val reset = aiResetDate(date)
        if(!config.enabled(cloud)) return aiCapabilities(false, false, "ai_unavailable", reset)
        return runCatching { tx { c ->
            val plan = entitlement(c, account, now.epochSecond) ?: return@tx aiCapabilities(false, false, "ai_unavailable", reset)
            val usage = c.rows("SELECT * FROM ai_monthly_usage WHERE account_id=? AND month=?", account, date.withDayOfMonth(1).toString()).firstOrNull()
            val exhausted = cloud && ((usage?.get("generations")?.jsonPrimitive?.long ?: 0) >= plan["generation_limit"]!!.jsonPrimitive.long ||
                config.rates!!.reservation > plan["cost_limit"]!!.jsonPrimitive.long - (usage?.get("cost")?.jsonPrimitive?.long ?: 0))
            aiCapabilities(true, !exhausted, if(exhausted) "ai_allowance_exhausted" else null, reset)
        } }.getOrElse { aiCapabilities(!cloud, false, "ai_accounting_unavailable", reset) }
    }
    /** Entitled cache reads stay free even when the month's allowance is exhausted. */
    fun cached(account: String, key: String): JsonObject? = tx { c ->
        val now = Instant.now().epochSecond
        if(!config.enabled(cloud) || entitlement(c, account, now) == null) return@tx null
        c.rows("SELECT result FROM ai_summary_cache WHERE cache_key=? AND expires_at>?", key, now).firstOrNull()?.get("result")?.jsonPrimitive?.content?.let { json.parseToJsonElement(it).jsonObject }
    }
    /** Reserves count and worst-case cost before any provider request, including retry leases. */
    fun reserve(account: String, project: String, operation: String, fingerprint: String, cacheKey: String? = null, now: Instant = Instant.now()): AiReservation = tx { c ->
        if(!config.enabled(cloud)) throw AiDenied("ai_unavailable")
        if (requireProject) {
            val id = project.removePrefix("proj_").toLongOrNull() ?: throw AiDenied("project_unavailable")
            if (c.rows("SELECT id FROM projects WHERE id=? AND deleted_at IS NULL FOR UPDATE", id).isEmpty()) throw AiDenied("project_unavailable")
        }
        val month = now.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).toString()
        val timestamp = now.epochSecond
        if(cloud) c.rows("SELECT account_id FROM ai_assignments WHERE account_id=? FOR UPDATE",account)
        c.insertOnce("ai_monthly_usage", "account_id,month", "?,?", account, month)
        val usage = c.rows("SELECT * FROM ai_monthly_usage WHERE account_id=? AND month=? FOR UPDATE", account, month).single()
        val plan = entitlement(c, account, timestamp) ?: throw AiDenied("ai_unavailable")
        // Recover abandoned counts; costs stay reserved because the provider might have billed.
        val abandoned = c.rows("SELECT operation_id FROM ai_operations WHERE account_id=? AND month=? AND state='pending' AND created_at<?", account, month, timestamp-120)
        if(abandoned.isNotEmpty()) {
            c.update("UPDATE ai_operations SET state='abandoned',result=NULL WHERE account_id=? AND month=? AND state='pending' AND created_at<?", account, month, timestamp-120)
            c.update("UPDATE ai_monthly_usage SET generations=generations-? WHERE account_id=? AND month=?", abandoned.size, account, month)
        }
        c.update("UPDATE ai_operations SET result=NULL WHERE account_id=? AND result_expires<=? AND result IS NOT NULL", account, timestamp)
        val previous = c.rows("SELECT * FROM ai_operations WHERE operation_id=?", operation).firstOrNull()
        if(previous != null) {
            if(previous["fingerprint"]!!.jsonPrimitive.content != fingerprint || previous["account_id"]!!.jsonPrimitive.content != account || previous["project_id"]!!.jsonPrimitive.content != project) throw AiDenied("ai_operation_conflict")
            val result = previous["result"]
            if(previous["state"]!!.jsonPrimitive.content == "success" && result != null && result != JsonNull && previous["result_expires"]!!.jsonPrimitive.long > timestamp) return@tx AiReservation(operation, json.parseToJsonElement(result.jsonPrimitive.content).jsonObject)
            throw AiDenied(if(previous["state"]!!.jsonPrimitive.content == "pending") "ai_operation_pending" else "ai_operation_expired")
        }
        if(cacheKey != null) {
            c.insertOnce("ai_summary_cache", "cache_key", "?", cacheKey)
            val cache = c.rows("SELECT * FROM ai_summary_cache WHERE cache_key=? FOR UPDATE", cacheKey).single()
            if(cache["expires_at"]!!.jsonPrimitive.long > timestamp && cache["result"] != JsonNull) return@tx AiReservation(operation, json.parseToJsonElement(cache["result"]!!.jsonPrimitive.content).jsonObject)
            if(cache["lease_until"]!!.jsonPrimitive.long > timestamp) throw AiDenied("ai_operation_pending")
        }
        val cost = config.rates?.reservation ?: 0L
        if(cloud) {
            if(usage["generations"]!!.jsonPrimitive.long - abandoned.size >= plan["generation_limit"]!!.jsonPrimitive.long || cost > plan["cost_limit"]!!.jsonPrimitive.long - usage["cost"]!!.jsonPrimitive.long) throw AiDenied("ai_allowance_exhausted")
            val recent = c.rows("SELECT COUNT(*) AS n FROM ai_operations WHERE account_id=? AND created_at>?", account, timestamp-60).single()["n"]!!.jsonPrimitive.long
            if(recent >= 20) throw AiDenied("ai_rate_limited")
        }
        c.update("INSERT INTO ai_operations(operation_id,account_id,project_id,month,fingerprint,state,reserved_cost,created_at,result_expires) VALUES(?,?,?,?,?,'pending',?,?,?)", operation, account, project, month, fingerprint, cost, timestamp, timestamp+900)
        c.update("UPDATE ai_monthly_usage SET generations=generations+1,cost=cost+? WHERE account_id=? AND month=?", cost, account, month)
        if(cacheKey != null) c.update("UPDATE ai_summary_cache SET operation_id=?,lease_until=? WHERE cache_key=?", operation, timestamp+120, cacheKey)
        AiReservation(operation)
    }
    /** Reconciles once against the original month; missing usage retains the reserved cost. */
    fun finish(id: String, success: JsonObject?, actualCost: Long?, cacheKey: String? = null) = tx { c ->
        val operation = c.rows("SELECT * FROM ai_operations WHERE operation_id=?", id).singleOrNull() ?: return@tx
        val account = operation["account_id"]!!.jsonPrimitive.content
        val month = operation["month"]!!.jsonPrimitive.content
        c.rows("SELECT * FROM ai_monthly_usage WHERE account_id=? AND month=? FOR UPDATE", account, month)
        val locked = c.rows("SELECT * FROM ai_operations WHERE operation_id=? FOR UPDATE", id).singleOrNull() ?: return@tx
        if(locked["state"]!!.jsonPrimitive.content != "pending") return@tx
        val reserved = locked["reserved_cost"]!!.jsonPrimitive.long
        c.update("UPDATE ai_monthly_usage SET generations=generations-?,cost=cost+? WHERE account_id=? AND month=?", if(success == null) 1 else 0, (actualCost ?: reserved)-reserved, account, month)
        c.update("UPDATE ai_operations SET state=?,result=?,result_expires=? WHERE operation_id=?", if(success == null) "failed" else "success", success?.toString(), Instant.now().epochSecond+900, id)
        if(cacheKey != null) c.update("UPDATE ai_summary_cache SET result=?,expires_at=?,lease_until=0 WHERE cache_key=? AND operation_id=?", success?.toString(), if(success == null) 0 else Instant.now().epochSecond+900, cacheKey, id)
    }
    /** Purges expired text and recovers abandoned counts while retaining all reserved costs. */
    fun cleanup(now: Instant = Instant.now()) {
        val expired = source.connection.use { c -> c.rows("SELECT DISTINCT account_id,month FROM ai_operations WHERE state='pending' AND created_at<?", now.epochSecond-120) }
        for(row in expired) tx { c ->
            val account=row["account_id"]!!.jsonPrimitive.content
            val month=row["month"]!!.jsonPrimitive.content
            c.rows("SELECT * FROM ai_monthly_usage WHERE account_id=? AND month=? FOR UPDATE",account,month)
            val count=c.update("UPDATE ai_operations SET state='abandoned',result=NULL WHERE account_id=? AND month=? AND state='pending' AND created_at<?",account,month,now.epochSecond-120)
            c.update("UPDATE ai_monthly_usage SET generations=generations-? WHERE account_id=? AND month=?",count,account,month)
        }
        tx { c ->
            c.update("UPDATE ai_operations SET result=NULL WHERE result IS NOT NULL AND result_expires<=?",now.epochSecond)
            c.update("UPDATE ai_summary_cache SET result=NULL WHERE result IS NOT NULL AND expires_at<=?",now.epochSecond)
        }
    }

    /** Operator plan and assignment writes have no customer-authenticated route. */
    fun operator(resource: String, id: String?, body: JsonObject?): JsonElement = tx { c ->
        when(resource) {
            "plans" -> if(body == null) JsonArray(c.rows("SELECT * FROM ai_plans")) else {
                require(id != null && id.matches(Regex("[A-Za-z0-9_.-]{1,100}")))
                val enabled = body["ai_enabled"]!!.jsonPrimitive.boolean
                val generations = body["generation_limit"]!!.jsonPrimitive.long
                val cost = body["cost_limit"]!!.jsonPrimitive.long
                require(generations in 0..1_000_000_000 && cost in 0..1_000_000_000_000)
                c.update("INSERT INTO ai_plans(plan_id,ai_enabled,generation_limit,cost_limit) VALUES(?,?,?,?) ON CONFLICT(plan_id) DO UPDATE SET ai_enabled=EXCLUDED.ai_enabled,generation_limit=EXCLUDED.generation_limit,cost_limit=EXCLUDED.cost_limit", id, enabled, generations, cost)
                buildJsonObject { put("ok", true) }
            }
            "assignments" -> if(body == null) JsonArray(c.rows("SELECT * FROM ai_assignments")) else {
                require(id != null && id.matches(Regex("[A-Za-z0-9_.-]{1,100}")))
                val plan = body["plan_id"]!!.jsonPrimitive.content
                val active = body["active"]!!.jsonPrimitive.boolean
                val expiry = body["expires_at"]?.takeIf { it != JsonNull }?.jsonPrimitive?.long
                require(c.rows("SELECT plan_id FROM ai_plans WHERE plan_id=?", plan).isNotEmpty())
                c.update("INSERT INTO ai_assignments(account_id,plan_id,active,expires_at) VALUES(?,?,?,?) ON CONFLICT(account_id) DO UPDATE SET plan_id=EXCLUDED.plan_id,active=EXCLUDED.active,expires_at=EXCLUDED.expires_at", id, plan, active, expiry)
                buildJsonObject { put("ok", true) }
            }
            "usage" -> { require(id != null); JsonArray(c.rows("SELECT * FROM ai_monthly_usage WHERE account_id=?", id)) }
            else -> throw IllegalArgumentException("Unknown operator resource")
        }
    }
}
