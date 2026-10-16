package com.quietmetrix.server.persistence

import com.quietmetrix.server.counters.CounterRegistryV2
import com.quietmetrix.server.domain.ProjectReportResponse
import com.quietmetrix.server.domain.ReportBin
import com.quietmetrix.server.domain.ReportCoverage
import com.quietmetrix.server.domain.ReportMeasure
import com.quietmetrix.server.domain.ReportRetentionCohort
import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Counters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.ZoneOffset

/** Select one stored projection. During rollout, a specific platform falls back cell-by-cell
 * to the pre-projection global row when that cell has no platform-specific copy yet. */
internal fun selectCounterProjection(rows: List<ResultRow>, platform: String): List<ResultRow> {
    val global = rows.filter { it[CounterCellsV2.segmentKind] == "global" && it[CounterCellsV2.segmentValue] == "all" }
    if (platform == "all") return global
    val platformRows = rows.filter { it[CounterCellsV2.segmentKind] == "platform" && it[CounterCellsV2.segmentValue] == platform }
    val projectedKeys = platformRows.map { Triple(it[CounterCellsV2.day], it[CounterCellsV2.hour], it[CounterCellsV2.dimsHash]) }.toSet()
    val legacyFallback = global.filter { row ->
        row[CounterCellsV2.platform] == platform && Triple(row[CounterCellsV2.day], row[CounterCellsV2.hour], row[CounterCellsV2.dimsHash]) !in projectedKeys
    }
    return platformRows + legacyFallback
}

/** A single reporting segment. Intersections are deliberately unsupported by stored projections. */
data class ReportSelection(val kind: String = "platform", val value: String = "all", val trafficScope: String = "all") {
    val platform: String get() = if (kind == "platform") value else "all"
    fun matchesUnique(dims: Map<String, String>): Boolean =
        dims["scope_kind"] == (if (kind == "screen") "screen" else "project") &&
            dims["scope_key"] == (if (kind == "screen") value else "all")
}

internal fun selectCounterProjection(rows: List<ResultRow>, selection: ReportSelection): List<ResultRow> {
    val ordinary = when (selection.kind) {
    "platform" -> selectCounterProjection(rows, selection.value)
    "country" -> rows.filter { it[CounterCellsV2.segmentKind] == "country" && it[CounterCellsV2.segmentValue] == selection.value }
    "screen" -> selectCounterProjection(rows, "all").filter { row ->
        val dims = runCatching { Json.parseToJsonElement(row[CounterCellsV2.dims]).jsonObject.mapValues { it.value.jsonPrimitive.content } }.getOrDefault(emptyMap())
        when (row[CounterCellsV2.metric]) {
            "unique_window_v2", "unique_hour_v2" -> selection.matchesUnique(dims)
            "screen_view_v2", "screen_dwell_ms_v2", "screen_dwell_sample_v2" -> dims["screen"] == selection.value
            "witness_window_v2" -> true // target hash is checked independently by windowWitnesses
            else -> false
        }
    }
    else -> emptyList()
    }
    if (selection.trafficScope == "all") return ordinary
    val key: (ResultRow) -> List<Any?> = { row -> listOf(
        row[CounterCellsV2.day], row[CounterCellsV2.hour], row[CounterCellsV2.metric],
        row[CounterCellsV2.platform], row[CounterCellsV2.appVersion], row[CounterCellsV2.country], row[CounterCellsV2.dimsHash],
    ) }
    val selectedKeys = ordinary.map(key).toSet()
    val classified = rows.filter { row ->
        row[CounterCellsV2.segmentKind] == "traffic_class" && key(row) in selectedKeys && when (selection.trafficScope) {
            "non_bot" -> row[CounterCellsV2.segmentValue] in setOf("likely_human", "unclassified")
            "likely_human", "unclassified", "detected_bot" -> row[CounterCellsV2.segmentValue] == selection.trafficScope
            else -> false
        }
    }
    val knownKeys = rows.filter { it[CounterCellsV2.segmentKind] == "traffic_class" }.map(key).toSet()
    val legacyUnknown = if (selection.trafficScope in setOf("non_bot", "unclassified")) ordinary.filter { key(it) !in knownKeys } else emptyList()
    return classified + legacyUnknown
}

/** Conservative v2 report foundation. People comes from exact fixed-window contributions;
 * daily People values are never added together. */
class ProjectReportRepository(private val database: Database) {
    fun read(projectId: Long, range: String, anchor: LocalDate, platform: String, compare: Boolean = false,
        filterKind: String = "platform", filterValue: String = platform, trafficScope: String = "all"): ProjectReportResponse =
        readSelected(projectId, range, anchor, ReportSelection(filterKind, filterValue, trafficScope), compare)

    private val json = Json { ignoreUnknownKeys = true }

    private fun readSelected(projectId: Long, range: String, anchor: LocalDate, selection: ReportSelection, compare: Boolean = false): ProjectReportResponse = transaction(database) {
        val days = when (range) { "today" -> 1; "7d" -> 7; "30d" -> 30; "90d" -> 90; else -> error("validated range required") }
        val from = if (range == "today") anchor else anchor.minusDays((days - 1).toLong())
        val to = anchor
        val people = peopleMeasure(projectId, days, to, selection)
        val traffic = listOf("detected_bot", "likely_human", "unclassified").associateWith { scope ->
            peopleMeasure(projectId, days, to, selection.copy(trafficScope = scope))
        }
        val peopleBins = peopleBins(projectId, range, from, to, selection)
        val charts = linkedMapOf(
            "people" to peopleBins,
            "visits" to activityBins(projectId, "visit_start_v2", "visits", peopleBins, range, selection),
            "events" to activityBins(projectId, "event_v2", "events", peopleBins, range, selection),
            "screen_views" to activityBins(projectId, "screen_view_v2", "views", peopleBins, range, selection),
            "crashes" to activityBins(projectId, "crash_v2", "crashes", peopleBins, range, selection),
            "visit_duration" to durationBins(projectId, peopleBins, range, selection),
        )
        val overview = linkedMapOf(
            "people" to people,
            "visits" to activityMeasure(projectId, "visit_start_v2", from, to, range, selection, people, "visits"),
            "events" to activityMeasure(projectId, "event_v2", from, to, range, selection, people, "events"),
            "crashes" to activityMeasure(projectId, "crash_v2", from, to, range, selection, people, "crashes"),
            "visit_duration" to averageVisitDuration(projectId, from, to, range, selection),
        )
        val previousFrom = if (range == "today") from.minusDays(1) else from.minusDays(days.toLong())
        val previousTo = from.minusDays(1)
        val comparisonPeopleBins = if (!compare) emptyList() else peopleBins(
            projectId, range, previousFrom, previousTo, selection,
            currentHourLimit = if (range == "today" && to == LocalDate.now(ZoneOffset.UTC)) java.time.Instant.now().atZone(ZoneOffset.UTC).hour else null,
        )
        val comparisonCharts = if (!compare) emptyMap() else linkedMapOf(
            "people" to comparisonPeopleBins,
            "visits" to activityBins(projectId, "visit_start_v2", "visits", comparisonPeopleBins, range, selection),
            "events" to activityBins(projectId, "event_v2", "events", comparisonPeopleBins, range, selection),
            "screen_views" to activityBins(projectId, "screen_view_v2", "views", comparisonPeopleBins, range, selection),
            "crashes" to activityBins(projectId, "crash_v2", "crashes", comparisonPeopleBins, range, selection),
            "visit_duration" to durationBins(projectId, comparisonPeopleBins, range, selection),
        )
        // The Overview headline always gets the prior-period People measure. The detailed
        // comparison series and retention cohorts remain gated by `compare` above/below.
        val previousPeople = if (range == "today") {
            mapOf("people" to ReportMeasure(state = "unsupported", unit = "installations", source = "v2"))
        } else {
            mapOf("people" to peopleMeasure(projectId, days, from.minusDays(1), selection))
        }
        val flow = mapOf(
            "screen_views" to activityMeasure(projectId, "screen_view_v2", from, to, range, selection, people, "views"),
            "transitions" to activityMeasure(projectId, "screen_transition_v2", from, to, range, selection, people, "transitions"),
        )
        val coverageRows = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to)
        }.toList()
        val legacyExists = Counters.selectAll().where { Counters.projectId eq projectId }.limit(1).any()
        val hasV2 = coverageRows.isNotEmpty()
        val hasHourly = coverageRows.any { it[CounterCellsV2.hour] >= 0 }
        val latest = coverageRows.maxOfOrNull { it[CounterCellsV2.updatedAt] }
        ProjectReportResponse(
            scope = mapOf("range" to range, "anchor" to anchor.toString(), "platform" to selection.platform, "filter_kind" to selection.kind, "filter_value" to selection.value, "traffic_scope" to selection.trafficScope, "timezone" to "UTC", "compare" to compare.toString()),
            generated_at = java.time.Instant.now().toString(),
            last_received_at = latest?.toInstant(ZoneOffset.UTC)?.toString(),
            coverage = ReportCoverage(
                source = when { hasV2 && legacyExists -> "mixed"; hasV2 -> "v2"; legacyExists -> "legacy"; else -> "v2" },
                resolution = if (range == "today" && hasHourly) "hourly" else "daily",
                oldest_day = coverageRows.minOfOrNull { it[CounterCellsV2.day] }?.toString(),
                newest_day = coverageRows.maxOfOrNull { it[CounterCellsV2.day] }?.toString(),
                sdk_versions = coverageRows.map { it[CounterCellsV2.sdkVersion] }.filter { it != "unknown" }.distinct().sorted(),
                legacy_present = legacyExists,
                traffic_class_available = coverageRows.any { it[CounterCellsV2.segmentKind] == "traffic_class" },
                traffic_class_supported = true,
            ),
            traffic = traffic,
            overview = overview,
            charts = charts,
            comparison_charts = comparisonCharts,
            comparison = previousPeople + if (compare) mapOf(
                "visit_duration" to if (range == "today") ReportMeasure(state="unsupported", unit="milliseconds", source="v2") else averageVisitDuration(projectId, previousFrom, previousTo, range, selection),
                "crashes" to activityMeasure(projectId, "crash_v2", previousFrom, previousTo, range, selection, previousPeople.getValue("people"), "crashes"),
            ) else emptyMap(),
            flow = flow,
            retention = retentionMeasures(projectId, from, to, selection),
            retention_cohorts = retentionCohorts(projectId, from, to, selection),
            comparison_retention_cohorts = if (compare) retentionCohorts(projectId, previousFrom, previousTo, selection) else emptyList(),
            definitions = definitionCatalog(projectId, from, to, selection),
        )
    }

    /** Labels are returned only when their own aggregate contributor count reaches k. */
    private fun definitionCatalog(projectId: Long, from: LocalDate, to: LocalDate, selection: ReportSelection): Map<String, List<String>> {
        val rows = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and
                (CounterCellsV2.metric inList listOf("screen_view_v2", "event_v2")) and
                (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to)
        }.toList().let { selectCounterProjection(it, selection) }.filter { it[CounterCellsV2.hour] == (-1).toShort() }
        fun labels(metric: String, dim: String) = rows.filter { it[CounterCellsV2.metric] == metric }
            .groupBy { it[CounterCellsV2.dims] }
            .mapNotNull { (_, cells) ->
                val dims = runCatching { json.parseToJsonElement(cells.first()[CounterCellsV2.dims]).jsonObject.mapValues { it.value.jsonPrimitive.content } }.getOrDefault(emptyMap())
                val label = dims[dim]
                label?.takeIf { it.isNotBlank() && windowWitnesses(projectId, metric, dims, from, to, selection, cells) >= 5 }
            }.distinct().sorted()
        return mapOf("screens" to labels("screen_view_v2", "screen"), "events" to labels("event_v2", "name"))
    }

    /** Mature, entry-day cohort counts; no future mark is inferred from an app install. */
    private fun retentionMeasures(projectId: Long, from: LocalDate, to: LocalDate, selection: ReportSelection): Map<String, ReportMeasure> {
        val rows = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and
                (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to)
        }.toList().let { selectCounterProjection(it, selection) }
        fun dims(row: ResultRow) = runCatching { json.parseToJsonElement(row[CounterCellsV2.dims]).jsonObject.mapValues { it.value.jsonPrimitive.content } }.getOrDefault(emptyMap())
        val entries = rows.filter { it[CounterCellsV2.metric] == "retention_entry_v2" }
            .map { it to dims(it) }.filter { it.second["entry_day"]?.let { day -> day >= from.toString() && day <= to.toString() } == true }
        val result = linkedMapOf<String, ReportMeasure>()
        for (age in listOf(1, 3, 7, 14, 30)) {
            val mature = entries.filter { (_, d) -> runCatching { LocalDate.parse(d.getValue("entry_day")).plusDays(age.toLong()) <= to }.getOrDefault(false) }
            val denominator = mature.sumOf { it.first[CounterCellsV2.contributors] }
            val key = "day_$age"
            if (entries.isEmpty()) {
                result[key] = ReportMeasure(state = "insufficient_data", unit = "installations", source = "v2")
                continue
            }
            if (mature.isEmpty()) {
                result[key] = ReportMeasure(state = "not_mature", unit = "installations", source = "v2")
                continue
            }
            if (denominator < 5) {
                result[key] = ReportMeasure(state = "suppressed", unit = "installations", source = "v2")
                continue
            }
            val matureDays = mature.mapNotNull { (_, d) -> d["entry_day"] }.toSet()
            val returns = rows.filter { row ->
                row[CounterCellsV2.metric] == "retention_return_v2" &&
                    runCatching { val d = dims(row); d["age_day"] == age.toString() && d["entry_day"] in matureDays }.getOrDefault(false)
            }
            val numerator = returns.sumOf { it[CounterCellsV2.n] }
            val witnesses = returns.sumOf { it[CounterCellsV2.contributors] }
            result[key] = when {
                numerator > denominator -> ReportMeasure(state = "suppressed", unit = "installations", source = "v2")
                numerator > 0 && witnesses < 5 -> ReportMeasure(state = "suppressed", unit = "installations", source = "v2")
                numerator == 0L -> ReportMeasure(value = 0, state = "zero", unit = "installations", source = "v2", contributors = denominator, rate = 0.0)
                else -> ReportMeasure(value = numerator, state = "reported", unit = "installations", source = "v2", contributors = denominator, rate = numerator.toDouble() / denominator)
            }
        }
        return result
    }

    /** Per-entry-day exact cohort cells used by the Retention heatmap and weighted weekly view. */
    private fun retentionCohorts(projectId: Long, from: LocalDate, to: LocalDate, selection: ReportSelection): List<ReportRetentionCohort> {
        val rows = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and
                (CounterCellsV2.metric inList listOf("retention_entry_v2", "retention_return_v2")) and
                (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to)
        }.toList().let { selectCounterProjection(it, selection) }.filter { it[CounterCellsV2.hour] == (-1).toShort() }
        fun dimensions(row: ResultRow): Map<String, String> = runCatching {
            json.parseToJsonElement(row[CounterCellsV2.dims]).jsonObject.mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
        val entries = rows.filter { it[CounterCellsV2.metric] == "retention_entry_v2" }
            .map { it to dimensions(it) }.filter { (_, dims) -> dims["entry_day"]?.let { it >= from.toString() && it <= to.toString() } == true }
            .groupBy { it.second.getValue("entry_day") }
        val returns = rows.filter { it[CounterCellsV2.metric] == "retention_return_v2" }
            .map { it to dimensions(it) }
        return entries.toSortedMap().map { (entryDay, group) ->
            val size = group.sumOf { it.first[CounterCellsV2.n] }
            val sizeWitnesses = group.sumOf { it.first[CounterCellsV2.contributors] }
            val sizeMeasure = if (sizeWitnesses < 5) ReportMeasure(state = "suppressed", unit = "installations", source = "v2")
                else ReportMeasure(value = size, state = if (size == 0L) "zero" else "reported", unit = "installations", source = "v2", contributors = sizeWitnesses)
            val days = listOf(1, 3, 7, 14, 30).associate { age ->
                val measure = when {
                    LocalDate.parse(entryDay).plusDays(age.toLong()).isAfter(to) -> ReportMeasure(state = "not_mature", unit = "installations", source = "v2")
                    sizeWitnesses < 5 -> ReportMeasure(state = "suppressed", unit = "installations", source = "v2")
                    else -> {
                        val matching = returns.filter { (_, dims) -> dims["entry_day"] == entryDay && dims["age_day"] == age.toString() }
                        val count = matching.sumOf { it.first[CounterCellsV2.n] }
                        val witnesses = matching.sumOf { it.first[CounterCellsV2.contributors] }
                        when {
                            count > size || (count > 0 && witnesses < 5) -> ReportMeasure(state = "suppressed", unit = "installations", source = "v2")
                            count == 0L -> ReportMeasure(value = 0, state = "zero", unit = "installations", source = "v2", contributors = sizeWitnesses, rate = 0.0)
                            else -> ReportMeasure(value = count, state = "reported", unit = "installations", source = "v2", contributors = sizeWitnesses, rate = count.toDouble() / size)
                        }
                    }
                }
                age.toString() to measure
            }
            ReportRetentionCohort(entryDay, sizeMeasure, days)
        }
    }

    /** Reads only the report resolution for this range and gates action counts by exact-window People. */
    private fun activityMeasure(
        projectId: Long, metric: String, from: LocalDate, to: LocalDate, range: String,
        selection: ReportSelection, people: ReportMeasure, unit: String, hour: Int? = null,
    ): ReportMeasure {
        val cells = activityCells(projectId, metric, from, to, range, selection, hour)
        if (cells.isEmpty()) return ReportMeasure(state = "insufficient_data", unit = unit, source = "v2")
        if (people.state != "reported") return ReportMeasure(state = people.state, unit = unit, source = "v2")
        val grouped = cells.groupBy { it[CounterCellsV2.dims] }.values.map { rows ->
            val dims = runCatching { json.parseToJsonElement(rows.first()[CounterCellsV2.dims]).jsonObject.mapValues { it.value.jsonPrimitive.content } }.getOrDefault(emptyMap())
            rows to (windowWitnesses(projectId, metric, dims, from, to, selection, rows) >= 5)
        }
        val eligible = grouped.filter { it.second }
        if (eligible.isEmpty()) return ReportMeasure(state = "suppressed", unit = unit, source = "v2")
        val value = eligible.sumOf { (rows, _) -> rows.sumOf { it[CounterCellsV2.n] } }
        return ReportMeasure(
            value = value, state = if (value == 0L) "zero" else "reported", unit = unit,
            source = "v2", contributors = people.value,
        )
    }

    /** Distinct contributors to this exact metric/dimensions/window. Legacy daily contributor
     * counts are used conservatively (maximum day, never summed across days). */
    private fun windowWitnesses(
        projectId: Long, metric: String, dims: Map<String, String>, from: LocalDate, to: LocalDate,
        selection: ReportSelection, legacyRows: List<ResultRow>,
    ): Long {
        val validation = CounterRegistryV2.validate(metric, dims) as? CounterRegistryV2.Result.Accepted
            ?: return 0
        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt() + 1
        if (days !in 1..7 && days !in setOf(30, 90)) return legacyRows.groupBy { it[CounterCellsV2.day] to it[CounterCellsV2.hour] }.values.maxOfOrNull { rows -> rows.sumOf { it[CounterCellsV2.contributors] } } ?: 0L
        val witnessRows = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and (CounterCellsV2.metric eq "witness_window_v2") and
                (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to)
        }.toList().let { selectCounterProjection(it, selection) }.filter { row ->
            runCatching {
                val witness = json.parseToJsonElement(row[CounterCellsV2.dims]).jsonObject
                witness["target_metric"]?.jsonPrimitive?.content == metric &&
                    witness["target_hash"]?.jsonPrimitive?.content == validation.value.hash &&
                    witness["days"]?.jsonPrimitive?.content?.toIntOrNull() == days &&
                    witness["end_day"]?.jsonPrimitive?.content == to.toString()
            }.getOrDefault(false)
        }
        return if (witnessRows.isNotEmpty()) witnessRows.sumOf { it[CounterCellsV2.n] }
        else legacyRows.groupBy { it[CounterCellsV2.day] to it[CounterCellsV2.hour] }.values.maxOfOrNull { rows -> rows.sumOf { it[CounterCellsV2.contributors] } } ?: 0L
    }

    /** Duration is a weighted mean: summed milliseconds divided by completed visit samples. */
    private fun averageVisitDuration(
        projectId: Long, from: LocalDate, to: LocalDate, range: String, selection: ReportSelection, hour: Int? = null,
    ): ReportMeasure {
        val durations = activityCells(projectId, "visit_duration_ms_v2", from, to, range, selection, hour)
        val completionRows = activityCells(projectId, "visit_complete_v2", from, to, range, selection, hour)
        val completions = completionRows.sumOf { it[CounterCellsV2.n] }
        if (durations.isEmpty() || completions == 0L) return ReportMeasure(state = "insufficient_data", unit = "milliseconds", source = "v2")
        val completionDims = runCatching { json.parseToJsonElement(completionRows.first()[CounterCellsV2.dims]).jsonObject.mapValues { it.value.jsonPrimitive.content } }.getOrDefault(emptyMap())
        if (completions < 5 || windowWitnesses(projectId, "visit_complete_v2", completionDims, from, to, selection, completionRows) < 5 ||
            windowWitnesses(projectId, "visit_duration_ms_v2", emptyMap(), from, to, selection, durations) < 5) {
            return ReportMeasure(state = "suppressed", unit = "milliseconds", source = "v2")
        }
        val totalMs = durations.sumOf { it[CounterCellsV2.n] }
        return ReportMeasure(value = totalMs / completions, state = "reported", unit = "milliseconds", source = "v2", contributors = completions)
    }

    private fun activityCells(
        projectId: Long, metric: String, from: LocalDate, to: LocalDate, range: String, selection: ReportSelection, hour: Int? = null,
    ) = CounterCellsV2.selectAll().where {
        (CounterCellsV2.projectId eq projectId) and (CounterCellsV2.metric eq metric) and
            (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to)
    }.toList().let { selectCounterProjection(it, selection) }.filter { row ->
            if (range == "today") row[CounterCellsV2.hour] >= 0 && (hour == null || row[CounterCellsV2.hour] == hour.toShort())
            else row[CounterCellsV2.hour] == (-1).toShort()
    }

    private fun activityBins(
        projectId: Long, metric: String, unit: String, bins: List<ReportBin>, range: String, selection: ReportSelection,
    ): List<ReportBin> = bins.map { bin ->
        val from = LocalDate.parse(bin.start_day)
        val to = LocalDate.parse(bin.end_day)
        val measure = activityMeasure(
            projectId, metric, from, to, range, selection,
            bin.measures.getValue("people"), unit, bin.hour,
        )
        val key = when (metric) { "visit_start_v2" -> "visits"; "event_v2" -> "events"; "crash_v2" -> "crashes"; "screen_view_v2" -> "views"; else -> metric.removeSuffix("_v2") }
        bin.copy(measures = bin.measures + (key to measure))
    }

    private fun durationBins(projectId: Long, bins: List<ReportBin>, range: String, selection: ReportSelection): List<ReportBin> = bins.map { bin ->
        val measure = averageVisitDuration(
            projectId, LocalDate.parse(bin.start_day), LocalDate.parse(bin.end_day), range, selection, bin.hour,
        )
        bin.copy(measures = bin.measures + ("visit_duration" to measure))
    }

    private fun peopleBins(
        projectId: Long, range: String, from: LocalDate, to: LocalDate, selection: ReportSelection, currentHourLimit: Int? = null,
    ): List<ReportBin> =
        if (range == "today") {
            val currentUtcDay = LocalDate.now(ZoneOffset.UTC)
            val lastHour = currentHourLimit ?: if (from == currentUtcDay) java.time.Instant.now().atZone(ZoneOffset.UTC).hour else 23
            (0..lastHour).map { hour ->
                val day = from
                ReportBin(day.toString(), day.toString(), hour, mapOf("people" to hourlyPeopleMeasure(projectId, day, hour, selection)))
            }
        } else if (range == "90d") {
            buildList {
                var start = from
                while (!start.isAfter(to)) {
                    val end = minOf(start.plusDays(6), to)
                    val length = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
                    add(ReportBin(start.toString(), end.toString(), measures = mapOf("people" to peopleMeasure(projectId, length, end, selection))))
                    start = end.plusDays(1)
                }
            }
        } else {
            generateSequence(from) { day -> if (day.isBefore(to)) day.plusDays(1) else null }
                .map { day -> ReportBin(day.toString(), day.toString(), measures = mapOf("people" to peopleMeasure(projectId, 1, day, selection))) }
                .toList()
        }

    private fun hourlyPeopleMeasure(projectId: Long, day: LocalDate, hour: Int, selection: ReportSelection): ReportMeasure {
        val rows = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and (CounterCellsV2.day eq day) and
                (CounterCellsV2.hour eq hour.toShort()) and (CounterCellsV2.metric eq "unique_hour_v2")
        }.toList().let { selectCounterProjection(it, selection) }.filter {
            runCatching {
                val d = json.parseToJsonElement(it[CounterCellsV2.dims]).jsonObject
                selection.matchesUnique(d.mapValues { it.value.jsonPrimitive.content })
            }.getOrDefault(false)
        }
        val witnesses = rows.sumOf { it[CounterCellsV2.contributors] }
        return when {
            rows.isEmpty() -> ReportMeasure(state = "insufficient_data", unit = "installations", source = "v2")
            witnesses < 5 -> ReportMeasure(state = "suppressed", unit = "installations", source = "v2")
            else -> rows.sumOf { it[CounterCellsV2.n] }.let { value -> ReportMeasure(value = value, state = if (value == 0L) "zero" else "reported", unit = "installations", source = "v2", contributors = witnesses) }
        }
    }

    private fun peopleMeasure(projectId: Long, days: Int, end: LocalDate, selection: ReportSelection): ReportMeasure {
        val start = end.minusDays((days - 1).toLong())
        val rows = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and
                (CounterCellsV2.metric eq "unique_window_v2") and
                (CounterCellsV2.day greaterEq start) and (CounterCellsV2.day lessEq end) and
                (CounterCellsV2.hour eq (-1).toShort())
        }.toList().let { selectCounterProjection(it, selection) }.filter {
            runCatching {
                val d = json.parseToJsonElement(it[CounterCellsV2.dims]).jsonObject
                selection.matchesUnique(d.mapValues { it.value.jsonPrimitive.content }) &&
                    d["days"]?.jsonPrimitive?.content?.toIntOrNull() == days &&
                    d["end_day"]?.jsonPrimitive?.content == end.toString()
            }.getOrDefault(false)
        }
        val value = rows.sumOf { it[CounterCellsV2.n] }
        val witnesses = rows.sumOf { it[CounterCellsV2.contributors] }
        return when {
            rows.isEmpty() -> ReportMeasure(state = "insufficient_data", unit = "installations", source = "v2")
            witnesses < 5 -> ReportMeasure(state = "suppressed", unit = "installations", source = "v2")
            else -> ReportMeasure(value = value, state = if (value == 0L) "zero" else "reported", unit = "installations", source = "v2", contributors = witnesses)
        }
    }
}
