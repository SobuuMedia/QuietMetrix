package com.quietmetrix.server.maintenance

import com.quietmetrix.server.config.AppConfig
import com.quietmetrix.server.persistence.tables.AuthSessions
import com.quietmetrix.server.persistence.tables.CounterBatchReceipts
import com.quietmetrix.server.persistence.tables.CounterBatchReceiptsV2
import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.CountersQuarantine
import com.quietmetrix.server.persistence.tables.EventCountsDaily
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.RequestRateBuckets
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.ktor.ext.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Analytics windows crossing the retained boundary are discarded rather than undercounted. */
class DataMaintenance(private val database: Database, private val config: AppConfig) {
    fun run(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): Int = transaction(database) {
        var deleted = 0
        val now = LocalDateTime.now(ZoneOffset.UTC)
        deleted += AuthSessions.deleteWhere { expiresAt lessEq now }
        deleted += RequestRateBuckets.deleteWhere { expiresAt lessEq now.toEpochSecond(ZoneOffset.UTC) }
        deleted += CounterBatchReceiptsV2.deleteWhere { expiresAt lessEq now }
        deleted += CounterBatchReceipts.deleteWhere { createdAt less now.minusDays(14) }
        deleted += CountersQuarantine.deleteWhere { quarantinedAt less now.minusDays(7) }
        // Historical diagnostics may contain arbitrary user-supplied fields. Never retain them.
        CountersQuarantine.update { it[payload] = "{}"; it[quarantineDetail] = null }
        Projects.selectAll().forEach { project ->
            val retention = config.retentionDays ?: if (config.isCloud) when (project[Projects.planId]) {
                "hobby" -> 90; "startup" -> 365; "business" -> 730; else -> 30
            } else 0
            if (retention <= 0) return@forEach
            val id = project[Projects.id]
            val cutoff = today.minusDays(retention.toLong() - 1)
            deleted += Counters.deleteWhere { (projectId eq id) and (day less cutoff) }
            deleted += EventCountsDaily.deleteWhere { (projectId eq id) and (day less cutoff) }
            deleted += CounterCellsV2.deleteWhere { (projectId eq id) and (day less cutoff) }
            val windows = CounterCellsV2.selectAll().where { (CounterCellsV2.projectId eq id) and (CounterCellsV2.metric inList listOf("unique_window_v2", "witness_window_v2", "funnel_step_v2", "retention_entry_v2", "retention_return_v2")) }.toList()
            windows.forEach { row ->
                val dims = runCatching { Json.parseToJsonElement(row[CounterCellsV2.dims]).jsonObject }.getOrNull() ?: return@forEach
                val end = dims["end_day"]?.jsonPrimitive?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val days = dims["days"]?.jsonPrimitive?.intOrNull
                val entry = dims["entry_day"]?.jsonPrimitive?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if ((entry != null && entry.isBefore(cutoff)) || (end != null && days != null && end.minusDays(days.toLong() - 1).isBefore(cutoff))) {
                    deleted += CounterCellsV2.deleteWhere {
                        (projectId eq id) and (day eq row[CounterCellsV2.day]) and (hour eq row[CounterCellsV2.hour]) and
                        (metric eq row[CounterCellsV2.metric]) and (platform eq row[CounterCellsV2.platform]) and
                        (appVersion eq row[CounterCellsV2.appVersion]) and (country eq row[CounterCellsV2.country]) and
                        (dimsHash eq row[CounterCellsV2.dimsHash]) and (segmentKind eq row[CounterCellsV2.segmentKind]) and
                        (segmentValue eq row[CounterCellsV2.segmentValue])
                    }
                }
            }
            Counters.selectAll().where { (Counters.projectId eq id) and (Counters.metric eq "unique_window_v2") }.toList().forEach { row ->
                val dims = runCatching { Json.parseToJsonElement(row[Counters.dims]).jsonObject }.getOrNull() ?: return@forEach
                val end = dims["end_day"]?.jsonPrimitive?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val days = dims["days"]?.jsonPrimitive?.intOrNull
                if (end != null && days != null && end.minusDays(days.toLong() - 1).isBefore(cutoff)) {
                    deleted += Counters.deleteWhere {
                        (projectId eq id) and (day eq row[Counters.day]) and (metric eq row[Counters.metric]) and
                        (platform eq row[Counters.platform]) and (appVersion eq row[Counters.appVersion]) and
                        (country eq row[Counters.country]) and (dimsHash eq row[Counters.dimsHash])
                    }
                }
            }
        }
        deleted
    }
}

fun Application.configureDataMaintenance(config: AppConfig) {
    val maintenance = DataMaintenance(get(), config)
    val job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        while (isActive) {
            runCatching { maintenance.run() }.onSuccess { if (it > 0) environment.log.info("Data maintenance removed $it expired rows") }
                .onFailure { environment.log.error("Data maintenance failed", it) }
            delay(60_000)
        }
    }
    monitor.subscribe(ApplicationStopped) { job.cancel() }
}
