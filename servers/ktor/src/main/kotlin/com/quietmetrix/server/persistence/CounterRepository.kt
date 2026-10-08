package com.quietmetrix.server.persistence

import com.quietmetrix.server.counters.CounterRegistry
import com.quietmetrix.server.persistence.tables.CounterBatchReceipts
import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.CountersQuarantine
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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
    data class FacetTotal(val name: String, val n: Long)

    sealed class UpsertResult {
        object Applied : UpsertResult()
        data class Quarantined(val reason: String) : UpsertResult()
    }

    sealed class BatchResult {
        data class Applied(val accepted: Int, val quarantined: Int) : BatchResult()
        data class Replayed(val accepted: Int, val quarantined: Int) : BatchResult()
        object PayloadConflict : BatchResult()
    }

    /** Applies a batch and its receipt as one transaction. A claim token distinguishes the
     * transaction that inserted the unique receipt from concurrent retries waiting on it. */
    fun upsertBatchOnce(
        projectId: Long,
        batchId: String,
        payloadHash: String,
        claimToken: String,
        day: LocalDate,
        deltas: List<CounterDelta>,
    ): BatchResult = transaction(database) {
        CounterBatchReceipts.insertIgnore {
            it[CounterBatchReceipts.projectId] = projectId
            it[CounterBatchReceipts.batchId] = batchId
            it[CounterBatchReceipts.payloadHash] = payloadHash
            it[CounterBatchReceipts.claimToken] = claimToken
            it[CounterBatchReceipts.accepted] = 0
            it[CounterBatchReceipts.quarantined] = 0
            it[CounterBatchReceipts.createdAt] = LocalDateTime.now()
        }
        val receipt = CounterBatchReceipts.selectAll()
            .where { (CounterBatchReceipts.projectId eq projectId) and (CounterBatchReceipts.batchId eq batchId) }
            .single()
        if (receipt[CounterBatchReceipts.claimToken] != claimToken) {
            return@transaction if (receipt[CounterBatchReceipts.payloadHash] != payloadHash) {
                BatchResult.PayloadConflict
            } else {
                BatchResult.Replayed(receipt[CounterBatchReceipts.accepted], receipt[CounterBatchReceipts.quarantined])
            }
        }

        val invalid = deltas.map { CounterRegistry.validate(it.metric, it.dims) }
        if (invalid.any { it is CounterRegistry.ValidationResult.Invalid }) {
            // Keep the batch atomic. Quarantine the full payload for diagnostics; no valid
            // sibling counter from a malformed batch is allowed to affect reports.
            deltas.forEachIndexed { index, delta ->
                val reason = (invalid[index] as? CounterRegistry.ValidationResult.Invalid)?.reason ?: "batch_invalid"
                quarantine(projectId, delta, reason, "batch $batchId rejected atomically")
            }
            CounterBatchReceipts.update({ (CounterBatchReceipts.projectId eq projectId) and (CounterBatchReceipts.batchId eq batchId) }) {
                it[CounterBatchReceipts.accepted] = 0
                it[CounterBatchReceipts.quarantined] = deltas.size
            }
            return@transaction BatchResult.Applied(0, deltas.size)
        }

        var accepted = 0
        var quarantined = 0
        deltas.forEach { delta ->
            when (upsert(projectId, day, delta)) {
                is UpsertResult.Applied -> accepted++
                is UpsertResult.Quarantined -> quarantined++
            }
        }
        CounterBatchReceipts.update({ (CounterBatchReceipts.projectId eq projectId) and (CounterBatchReceipts.batchId eq batchId) }) {
            it[CounterBatchReceipts.accepted] = accepted
            it[CounterBatchReceipts.quarantined] = quarantined
        }
        BatchResult.Applied(accepted, quarantined)
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
                // The cap limits distinct DIMS COMBINATIONS (dims_hash) per metric, not rows.
                // A single combination produces one row per (day, platform, app_version,
                // country), so counting rows here would trip the cap on a single popular
                // metric within weeks even with no new combinations. Only check the cap when
                // this dims_hash has never been seen before for this metric.
                val dimsHashSeenBefore = Counters.selectAll()
                    .where {
                        (Counters.projectId eq projectId) and
                            (Counters.metric eq delta.metric) and
                            (Counters.dimsHash eq validation.dimsHash)
                    }
                    .limit(1)
                    .any()
                if (!dimsHashSeenBefore) {
                    val distinctCombinations = Counters
                        .select(Counters.dimsHash)
                        .where { (Counters.projectId eq projectId) and (Counters.metric eq delta.metric) }
                        .withDistinct()
                        .count()
                    if (distinctCombinations >= maxDistinctCellsPerMetric) {
                        quarantine(projectId, delta, "cardinality_cap", "metric ${delta.metric} already has $distinctCombinations distinct dims combinations")
                        return@transaction UpsertResult.Quarantined("cardinality_cap")
                    }
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
     * app_version/country. A group is included only when its conservative distinct-installation lower bound is at least
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
                    val totalDevices = rows.maxOfOrNull { it[Counters.devices] } ?: 0L
                    if (totalDevices < k) return@mapNotNull null
                    CounterCell(
                        dims = json.decodeFromString(rows.first()[Counters.dims]),
                        n = rows.sumOf { it[Counters.n] },
                        devices = totalDevices,
                    )
                }
        }

    fun readTransitionCells(projectId: Long, from: LocalDate, to: LocalDate, platform: String, k: Int = kThreshold): List<CounterCell> = transaction(database) {
        Counters.selectAll().where {
            (Counters.projectId eq projectId) and (Counters.metric eq "screen_transition") and
                (Counters.day greaterEq from) and (Counters.day lessEq to)
        }.toList().filter { platform == "all" || it[Counters.platform] == platform }
            .groupBy { it[Counters.dimsHash] }.mapNotNull { (_, rows) ->
                val devices = rows.maxOfOrNull { it[Counters.devices] } ?: 0L
                if (devices < k) null else CounterCell(json.decodeFromString(rows.first()[Counters.dims]), rows.sumOf { it[Counters.n] }, devices)
            }
    }

    /** A single country or platform projection for one exact unique-installation window. */
    fun uniqueWindowByFacet(
        projectId: Long,
        from: LocalDate,
        to: LocalDate,
        days: Int,
        endDay: LocalDate,
        facet: String,
        k: Int = kThreshold,
    ): List<FacetTotal> {
        require(facet == "country" || facet == "platform" || facet == "device_class")
        return transaction(database) {
            val v2 = CounterCellsV2.selectAll().where {
                (CounterCellsV2.projectId eq projectId) and (CounterCellsV2.metric eq "unique_window_v2") and
                    (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to) and
                    (CounterCellsV2.hour eq (-1).toShort())
            }.toList().filter { row -> runCatching {
                val dims = json.decodeFromString<Map<String, String>>(row[CounterCellsV2.dims])
                dims["scope_kind"] == "project" && dims["scope_key"] == "all" &&
                    dims["days"] == days.toString() && dims["end_day"] == endDay.toString()
            }.getOrDefault(false) }
            if (v2.isNotEmpty()) {
                val projected = v2.filter { it[CounterCellsV2.segmentKind] == facet }
                val specificKeys = projected.map { row -> listOf(row[CounterCellsV2.day], row[CounterCellsV2.dimsHash], row[CounterCellsV2.platform], row[CounterCellsV2.appVersion], row[CounterCellsV2.country]).joinToString("|") }.toSet()
                val legacy = if (facet == "device_class") emptyList() else v2.filter { row ->
                    row[CounterCellsV2.segmentKind] == "global" && listOf(row[CounterCellsV2.day], row[CounterCellsV2.dimsHash], row[CounterCellsV2.platform], row[CounterCellsV2.appVersion], row[CounterCellsV2.country]).joinToString("|") !in specificKeys
                }
                val values = projected.map { it to it[CounterCellsV2.segmentValue] } + legacy.map { row ->
                    row to when (facet) { "platform" -> row[CounterCellsV2.platform]; "country" -> row[CounterCellsV2.country].ifBlank { "unknown" }; else -> "unknown" }
                }
                return@transaction values.groupBy { it.second }.mapNotNull { (name, cells) ->
                    val witnesses = cells.sumOf { it.first[CounterCellsV2.contributors] }
                    if (witnesses < k) null else FacetTotal(name, cells.sumOf { it.first[CounterCellsV2.n] })
                }.sortedByDescending { it.n }
            }
            if (facet == "device_class") return@transaction emptyList()
            Counters.selectAll().where {
                (Counters.projectId eq projectId) and
                    (Counters.metric eq "unique_window_v2") and
                    (Counters.day greaterEq from) and (Counters.day lessEq to)
            }.toList()
                .filter { row ->
                    val dims = json.decodeFromString<Map<String, String>>(row[Counters.dims])
                    dims["scope_kind"] == "project" && dims["scope_key"] == "all" &&
                        dims["days"] == days.toString() && dims["end_day"] == endDay.toString()
                }
                .groupBy { row ->
                    val value = if (facet == "country") row[Counters.country] else row[Counters.platform]
                    value.ifBlank { "unknown" }
                }
                .mapNotNull { (name, rows) ->
                    if (rows.sumOf { it[Counters.devices] } < k) null
                    else FacetTotal(name, rows.sumOf { it[Counters.n] })
                }
                .sortedByDescending { it.n }
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
                    val totalDevices = rows.maxOfOrNull { it[Counters.devices] } ?: 0L
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
                    val totalDevices = rows.maxOfOrNull { it[Counters.devices] } ?: 0L
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
                        "metric" to "[redacted]",
                        "dimension_count" to delta.dims.size.toString(),
                    ),
                )
                it[quarantineReason] = reason
                it[quarantineDetail] = null
                it[quarantinedAt] = LocalDateTime.now()
            }
        }
    }
}
