package com.quietmetrix.server.persistence

import com.quietmetrix.server.counters.CounterRegistry
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.CountersQuarantine
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Persists counter cells: `(project, day, metric, platform, app_version, country, dims) -> n`.
 * Nothing here ever sees or stores an install identifier — [CounterDelta.isNewDevice] is the
 * SDK's own "first flush of this cell today" flag, so [readCells]'s k-anonymity filter can
 * work from a running device count alone. See [CounterRegistry] for the validation and
 * canonicalization every write passes through first.
 */
class CounterRepository(
    private val database: Database,
    private val kThreshold: Int = 5,
    private val maxDistinctCellsPerMetric: Int = 500,
) {

    private val json = Json { encodeDefaults = true }

    /** One inbound delta from an SDK flush, prior to validation. */
    data class CounterDelta(
        val metric: String,
        val dims: Map<String, String>,
        val platform: String = "",
        val appVersion: String = "",
        val country: String = "",
        val n: Long,
        /** The SDK's own flag: true only on the first flush that touched this exact cell
         *  today. Summed across flushes this becomes a distinct-device count with no
         *  identifier ever existing. */
        val isNewDevice: Boolean,
    )

    /** A counter cell as read back for display, aggregated across the requested date range. */
    data class CounterCell(val dims: Map<String, String>, val n: Long, val devices: Long)

    sealed class UpsertResult {
        object Applied : UpsertResult()
        data class Quarantined(val reason: String) : UpsertResult()
    }

    fun upsert(projectId: Long, day: LocalDate, delta: CounterDelta): UpsertResult {
        val validation = CounterRegistry.validate(delta.metric, delta.dims)
        if (validation is CounterRegistry.ValidationResult.Invalid) {
            quarantine(projectId, delta, validation.reason, null)
            return UpsertResult.Quarantined(validation.reason)
        }
        validation as CounterRegistry.ValidationResult.Valid

        fun cellMatch(): Op<Boolean> =
            (Counters.projectId eq projectId) and
                (Counters.day eq day) and
                (Counters.metric eq delta.metric) and
                (Counters.platform eq delta.platform) and
                (Counters.appVersion eq delta.appVersion) and
                (Counters.country eq delta.country) and
                (Counters.dimsHash eq validation.dimsHash)

        return transaction(database) {
            val existing = Counters.selectAll().where { cellMatch() }.limit(1).firstOrNull()
            val deviceDelta = if (delta.isNewDevice) 1L else 0L

            if (existing == null) {
                val distinctCells = Counters.selectAll()
                    .where { (Counters.projectId eq projectId) and (Counters.metric eq delta.metric) }
                    .count()
                if (distinctCells >= maxDistinctCellsPerMetric) {
                    quarantine(projectId, delta, "cardinality_cap", "metric ${delta.metric} already has $distinctCells distinct cells")
                    return@transaction UpsertResult.Quarantined("cardinality_cap")
                }
                Counters.insert {
                    it[Counters.projectId] = projectId
                    it[Counters.day] = day
                    it[metric] = delta.metric
                    it[platform] = delta.platform
                    it[appVersion] = delta.appVersion
                    it[country] = delta.country
                    it[dimsHash] = validation.dimsHash
                    it[dims] = json.encodeToString(delta.dims)
                    it[n] = delta.n
                    it[devices] = deviceDelta
                    it[updatedAt] = LocalDateTime.now()
                }
            } else {
                val newN = existing[Counters.n] + delta.n
                val newDevices = existing[Counters.devices] + deviceDelta
                Counters.update({ cellMatch() }) {
                    it[n] = newN
                    it[devices] = newDevices
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            UpsertResult.Applied
        }
    }

    /**
     * Cells for [metric] in `[from, to]`, grouped by dims and summed across day/platform/
     * app_version/country. A group is included only when its summed device count is at least
     * [k] — the k-anonymity gate. Every read path onto `counters` must go through this, never
     * a raw select.
     */
    fun readCells(projectId: Long, metric: String, from: LocalDate, to: LocalDate, k: Int = kThreshold): List<CounterCell> =
        transaction(database) {
            Counters.selectAll()
                .where {
                    (Counters.projectId eq projectId) and
                        (Counters.metric eq metric) and
                        (Counters.day greaterEq from) and
                        (Counters.day lessEq to)
                }
                .toList()
                .groupBy { it[Counters.dimsHash] }
                .mapNotNull { (_, rows) ->
                    val totalDevices = rows.sumOf { it[Counters.devices] }
                    if (totalDevices < k) return@mapNotNull null
                    CounterCell(
                        dims = json.decodeFromString(rows.first()[Counters.dims]),
                        n = rows.sumOf { it[Counters.n] },
                        devices = totalDevices,
                    )
                }
        }

    /** One day's total for [DailyTotals] — day-preserving, unlike [readCells] which collapses
     *  the whole requested range into one number per dims_hash. */
    data class DailyTotal(val day: LocalDate, val n: Long)

    /**
     * Per-day totals for [metric] across all its dims, e.g. a "total events per day" trend.
     * The k-anonymity gate is applied per `(day, dims_hash)` cell, not once against the whole
     * range — stricter than [readCells], deliberately: a day whose own cell falls below [k]
     * must not surface a day-level count for it, even if the range-wide total would clear the
     * bar. Cells for different dims on the same day (e.g. distinct event names) are summed
     * into that day's total.
     */
    fun dailyTotals(projectId: Long, metric: String, from: LocalDate, to: LocalDate, k: Int = kThreshold): List<DailyTotal> =
        transaction(database) {
            Counters.selectAll()
                .where {
                    (Counters.projectId eq projectId) and
                        (Counters.metric eq metric) and
                        (Counters.day greaterEq from) and
                        (Counters.day lessEq to)
                }
                .toList()
                .groupBy { it[Counters.day] to it[Counters.dimsHash] }
                .mapNotNull { (key, rows) ->
                    val totalDevices = rows.sumOf { it[Counters.devices] }
                    if (totalDevices < k) return@mapNotNull null
                    key.first to rows.sumOf { it[Counters.n] }
                }
                .groupBy({ it.first }, { it.second })
                .map { (day, ns) -> DailyTotal(day, ns.sum()) }
                .sortedBy { it.day }
        }

    /** One platform's total for [totalsByPlatform]. */
    data class PlatformTotal(val platform: String, val n: Long)

    /**
     * A coarse per-platform breakdown of [metric]'s total volume — groups by the `platform`
     * column (a first-class column on `counters`, populated from the SDK's own `DeviceContext
     * .platform`), not by dims. Same per-cell k-anonymity gate as [dailyTotals]: a
     * `(platform, dims_hash)` cell below [k] contributes nothing, even blank/unknown platform
     * values (an empty platform string is dropped, not shown as an "unknown" bucket).
     */
    fun totalsByPlatform(projectId: Long, metric: String, from: LocalDate, to: LocalDate, k: Int = kThreshold): List<PlatformTotal> =
        transaction(database) {
            Counters.selectAll()
                .where {
                    (Counters.projectId eq projectId) and
                        (Counters.metric eq metric) and
                        (Counters.day greaterEq from) and
                        (Counters.day lessEq to)
                }
                .toList()
                .groupBy { it[Counters.platform] to it[Counters.dimsHash] }
                .mapNotNull { (key, rows) ->
                    val totalDevices = rows.sumOf { it[Counters.devices] }
                    if (totalDevices < k) return@mapNotNull null
                    key.first to rows.sumOf { it[Counters.n] }
                }
                .groupBy({ it.first }, { it.second })
                .map { (platform, ns) -> PlatformTotal(platform, ns.sum()) }
                .filter { it.platform.isNotBlank() }
                .sortedByDescending { it.n }
        }

    fun countQuarantined(projectId: Long): Long = transaction(database) {
        CountersQuarantine.selectAll().where { CountersQuarantine.projectId eq projectId }.count()
    }

    private fun quarantine(projectId: Long, delta: CounterDelta, reason: String, detail: String?) {
        transaction(database) {
            CountersQuarantine.insert {
                it[CountersQuarantine.projectId] = projectId
                it[payload] = json.encodeToString(
                    mapOf(
                        "metric" to delta.metric,
                        "dims" to delta.dims.toString(),
                        "n" to delta.n.toString(),
                    ),
                )
                it[quarantineReason] = reason
                it[quarantineDetail] = detail
                it[quarantinedAt] = LocalDateTime.now()
            }
        }
    }
}
