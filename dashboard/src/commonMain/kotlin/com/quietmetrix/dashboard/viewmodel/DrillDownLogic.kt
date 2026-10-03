package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.*
import com.quietmetrix.dashboard.format.*
import com.quietmetrix.dashboard.resources.*
import com.quietmetrix.dashboard.ui.screens.analyticsChartPoints
import com.quietmetrix.dashboard.ui.screens.computeRetentionVitals
import com.quietmetrix.dashboard.ui.screens.epochDayFromIsoOrNull
import com.quietmetrix.dashboard.ui.components.CohortGrouping
import com.quietmetrix.dashboard.ui.components.buildCohortHeatmapRows
import org.jetbrains.compose.resources.StringResource

/** A stable dataset key; values are resolved again whenever dashboard data changes. */
data class DrillDownSelection(val kind: String, val key: String)

/** One visible breakdown value, with optional numeric data for the mini chart. */
data class DrillDownRow(val label: String, val value: String, val numeric: Float? = null, val labelRes: StringResource? = null)

/** Localized drawer content built exclusively from already privacy-filtered responses. */
data class DrillDownContent(
    val kind: String, val key: String, val value: String, val available: Boolean,
    val titleRes: StringResource?, val explanation: StringResource,
    val breakdownTitle: StringResource = Res.string.drill_breakdown,
    val rows: List<DrillDownRow> = emptyList(), val previousValue: String? = null, val title: String? = null, val rawValue: String? = null,
)

/** Resolves current data and, when enabled, the corresponding previous-period selection. */
internal fun selectDrillDown(state: DashboardState, kind: String, key: String): DrillDownContent {
    val selected = resolveDrillDown(state, kind, key)
    if (!state.compare) return selected
    val previous = state.copy(aggregates = state.comparison.aggregates, sessions = state.comparison.sessions,
        retention = state.comparison.retention, transitions = state.comparison.transitions, funnelResults = state.comparison.funnel)
    val days = ((state.range.seconds + 86399) / 86400).coerceAtLeast(1)
    val previousKey = if (kind.startsWith("day-")) {
        val day = epochDayFromIsoOrNull(key)
        val raw = if (kind == "day-visits") previous.sessions?.dailySessions else previous.aggregates?.daily
        val points = analyticsChartPoints(raw.orEmpty(), state.range, state.analyticsAnchor, previous = true)
        points.orEmpty().firstOrNull { epochDayFromIsoOrNull(it.day) == day?.minus(days) }?.day ?: ""
    } else if (kind == "cohort") {
        val target = epochDayFromIsoOrNull(key)?.minus(days)
        previous.retention?.cohorts.orEmpty().firstOrNull { target != null && epochDayFromIsoOrNull(it.cohortDate) == target }?.cohortDate ?: ""
    } else if (kind == "cohort-week") {
        val row = buildCohortHeatmapRows(state.retention?.cohorts.orEmpty(), CohortGrouping.ByWeek).firstOrNull { it.id == key }
        val target = row?.label?.let(::epochDayFromIsoOrNull)?.minus(days)
        buildCohortHeatmapRows(previous.retention?.cohorts.orEmpty(), CohortGrouping.ByWeek)
            .firstOrNull { target != null && epochDayFromIsoOrNull(it.label) == target }?.id ?: ""
    } else key
    val prior = resolveDrillDown(previous, kind, previousKey, previousPeriod = true)
    return selected.copy(previousValue = prior.value.takeIf { prior.available })
}

/** Resolves a stable metric key and retains missing, suppressed, and genuinely zero values. */
private fun resolveDrillDown(state: DashboardState, kind: String, key: String, previousPeriod: Boolean = false): DrillDownContent {
    var displayTitle: String? = null
    var title: StringResource? = null
    var explanation = Res.string.drill_breakdown_detail
    var breakdownTitle = Res.string.drill_breakdown
    var rows = emptyList<DrillDownRow>()
    var fraction = false
    val value: Number? = when (kind) {
        "screen" -> {
            explanation = Res.string.drill_screen_detail
            rows = state.aggregates?.topScreens.orEmpty().map { DrillDownRow(it.screen.orEmpty(), formatCount(it.count), it.count.toFloat()) }
            state.aggregates?.topScreens?.firstOrNull { it.screen == key }?.count
        }
        "flow" -> {
            explanation = Res.string.drill_flow_detail
            val outgoing = state.transitions?.transitions?.filter { it.fromScreen == key }
            rows = outgoing.orEmpty().map { DrillDownRow(it.toScreen.orEmpty(), formatCount(it.count), it.count.toFloat()) }
            outgoing?.takeIf { it.isNotEmpty() }?.sumOf { it.count }
        }
        "platform", "country", "device" -> {
            val items = when (kind) { "platform" -> state.aggregates?.platforms; "country" -> state.aggregates?.countries; else -> state.aggregates?.deviceClasses }
            rows = items.orEmpty().map { DrillDownRow(it.displayName, formatCount(it.count), it.count.toFloat()) }
            items?.firstOrNull { it.displayName == key }?.count
        }
        "sessions", "duration" -> {
            title = if (kind == "sessions") Res.string.overview_vital_sessions_label else Res.string.overview_vital_duration_label
            explanation = if (kind == "sessions") Res.string.drill_sessions_detail else Res.string.drill_duration_detail
            breakdownTitle = Res.string.drill_daily
            rows = state.sessions?.dailySessions.orEmpty().map { DrillDownRow(it.day, formatCount(it.total), it.total.toFloat()) }
            if (kind == "sessions") state.sessions?.totalSessions else state.sessions?.avgDurationSec
        }
        "errors" -> {
            title = Res.string.kpi_errors
            explanation = Res.string.drill_errors_detail
            rows = state.aggregates?.topEvents.orEmpty().filter { it.eventName.lowercase() in setOf("error", "crash", "exception") }
                .map { DrillDownRow(it.eventName, formatCount(it.count), it.count.toFloat()) }
            state.aggregates?.totals?.errors
        }
        "retention" -> {
            explanation = Res.string.drill_retention_detail
            fraction = true
            val vitals = computeRetentionVitals(state.retention)
            val day = when (key) { "day1" -> 1; "day7" -> 7; "day30" -> 30; else -> 0 }
            title = when (day) { 1 -> Res.string.retention_card_day1_label; 7 -> Res.string.retention_card_day7_label; 30 -> Res.string.retention_card_day30_label; else -> null }
            rows = state.retention?.cohorts.orEmpty().map { cohort ->
                val rate = when (day) { 1 -> cohort.day1; 7 -> cohort.day7; 30 -> cohort.day30; else -> null }
                DrillDownRow(cohort.cohortDate, rate?.let(::formatPercent) ?: "—", rate?.times(100)?.toFloat())
            }
            when (day) { 1 -> vitals.day1; 7 -> vitals.day7; 30 -> vitals.day30; else -> null }
        }
        "cohort", "cohort-week" -> {
            explanation = Res.string.drill_cohort_detail
            if (kind == "cohort-week") {
                val row = buildCohortHeatmapRows(state.retention?.cohorts.orEmpty(), CohortGrouping.ByWeek).firstOrNull { it.id == key }
                displayTitle = row?.label
                rows = row?.cells.orEmpty().mapIndexed { index, cell ->
                    DrillDownRow("D${listOf(1,3,7,14,30)[index]}", cell.fraction?.let(::formatPercent) ?: "—", cell.fraction?.times(100)?.toFloat())
                }
                row?.size
            } else {
                val cohort = state.retention?.cohorts?.firstOrNull { it.cohortDate == key }
                rows = cohort?.let { listOf(it.day1, it.day3, it.day7, it.day14, it.day30).mapIndexed { index, rate ->
                    DrillDownRow("D${listOf(1,3,7,14,30)[index]}", rate?.let(::formatPercent) ?: "—", rate?.times(100)?.toFloat())
                } }.orEmpty()
                cohort?.size
            }
        }
        "day-actions", "day-visits" -> {
            explanation = if (state.range == TimeRange.Day90) Res.string.drill_week_detail else Res.string.drill_day_detail
            breakdownTitle = Res.string.drill_daily
            val raw = if (kind == "day-visits") state.sessions?.dailySessions else state.aggregates?.daily
            val points = analyticsChartPoints(raw.orEmpty(), state.range, state.analyticsAnchor, previousPeriod)
            rows = points.orEmpty().map { DrillDownRow(it.day, formatCount(it.total), it.total.toFloat()) }
            points.firstOrNull { it.day == key }?.total
        }
        "funnel" -> {
            explanation = Res.string.drill_funnel_detail
            rows = state.funnelResults?.steps.orEmpty().map { DrillDownRow(it.name ?: it.key, formatCount(it.count.toLong()), it.count.toFloat()) }
            title = when (key) { "started" -> Res.string.funnel_kpi_started; "completed" -> Res.string.funnel_kpi_completed; "rate" -> Res.string.funnel_kpi_success_rate; else -> null }
            fraction = key == "rate"
            when (key) { "started" -> state.funnelResults?.entered; "completed" -> state.funnelResults?.converted; "rate" -> state.funnelResults?.overallConversion; else -> state.funnelResults?.steps?.firstOrNull { it.key == key }?.count }
        }
        else -> null
    }
    val rendered = when {
        value == null -> "—"
        fraction -> formatPercent(value.toDouble())
        kind == "duration" -> formatDuration(value.toInt())
        else -> formatCount(value.toLong())
    }
    return DrillDownContent(kind, key, rendered, value != null, title,
        if (value == null) Res.string.drill_unavailable else explanation, breakdownTitle, rows, title = displayTitle, rawValue = value?.toString())
}
