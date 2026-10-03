package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.format.formatCount
import com.quietmetrix.dashboard.format.formatDuration

/** Resolves a drill-down key against the currently loaded, privacy-filtered dashboard data. */
fun selectDrillDown(state: DashboardState, kind: String, key: String): DrillDownContent {
    val value = when (kind) {
        "screen" -> state.aggregates?.topScreens?.firstOrNull { it.screen == key }?.count
        "platform" -> state.aggregates?.platforms?.firstOrNull { it.displayName == key }?.count
        "country" -> state.aggregates?.countries?.firstOrNull { it.displayName == key }?.count
        "funnel" -> state.funnelResults?.let { result ->
            when (key) {
                "Started" -> result.entered.toLong()
                "Completed" -> result.converted.toLong()
                "Success rate" -> result.overallConversion * 100.0
                else -> result.steps.firstOrNull { it.key == key || it.name == key }?.count?.toLong()
            }
        }
        "retention" -> key.toDoubleOrNull()?.let { it * 100.0 }
        "sessions" -> state.sessions?.totalSessions?.toLong()
        "duration" -> state.sessions?.avgDurationSec?.toLong()
        "errors" -> state.aggregates?.let { it.totals.errors }
        else -> null
    }
    val rendered = when {
        kind == "duration" && value is Number -> formatDuration(value.toInt())
        value is Double -> "${value.toInt()}%"
        value is Number -> formatCount(value.toLong())
        else -> "—"
    }
    return DrillDownContent(kind, key, rendered, if (value == null) "No matching visible value is available for this selection." else "Based on the selected project and date range.")
}
