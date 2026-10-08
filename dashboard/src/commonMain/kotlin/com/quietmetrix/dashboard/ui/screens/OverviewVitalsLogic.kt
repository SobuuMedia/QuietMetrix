package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.RetentionResponse

/** Only the Overview vital that needs cohort selection logic. Other card values use their
 * explicitly named API fields directly and must never fall back to an unrelated measure. */
internal data class VitalsNumbers(val retentionDay1: Double?)

internal enum class OverviewMeasureSource { ReportedV2, Suppressed, LegacyApproximate, Unavailable }
internal data class OverviewDurationValue(val seconds: Int?, val source: OverviewMeasureSource)
internal data class OverviewCrashValue(val count: Long?, val source: OverviewMeasureSource)

/** Prefer exact v2 duration; never fall back to a legacy estimate when v2 explicitly suppressed it. */
internal fun resolveOverviewDuration(v2Milliseconds: Long?, v2State: String?, legacySeconds: Int?): OverviewDurationValue = when {
    v2State == "reported" && v2Milliseconds != null -> OverviewDurationValue(
        (v2Milliseconds / 1_000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(), OverviewMeasureSource.ReportedV2,
    )
    v2State == "suppressed" -> OverviewDurationValue(null, OverviewMeasureSource.Suppressed)
    legacySeconds != null -> OverviewDurationValue(legacySeconds, OverviewMeasureSource.LegacyApproximate)
    else -> OverviewDurationValue(null, OverviewMeasureSource.Unavailable)
}

internal fun resolveOverviewCrashes(v2Count: Long?, v2State: String?): OverviewCrashValue = when {
    v2State == "reported" && v2Count != null -> OverviewCrashValue(v2Count, OverviewMeasureSource.ReportedV2)
    v2State == "suppressed" -> OverviewCrashValue(null, OverviewMeasureSource.Suppressed)
    else -> OverviewCrashValue(null, OverviewMeasureSource.Unavailable)
}

/** Selects the latest mature, reportable day-one cohort. Missing data remains unavailable. */
internal fun computeVitalsNumbers(retention: RetentionResponse?): VitalsNumbers? {
    if (retention == null) return null
    val day1 = retention.cohorts
        .filter { it.day1 != null && it.size > 0 }
        .maxByOrNull { it.cohortDate }
        ?.day1
    return VitalsNumbers(day1)
}
