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
    v2State in setOf("reported", "zero") && v2Milliseconds != null -> OverviewDurationValue(
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

/** Size-weighted rate across reportable, mature entry cohorts. Suppressed cohorts are excluded. */
internal fun weightedRetentionRate(retention: RetentionResponse?, age: Int): Double? {
    val rows = retention?.cohorts.orEmpty().mapNotNull { cohort ->
        val rate = when (age) { 1 -> cohort.day1; 7 -> cohort.day7; 30 -> cohort.day30; else -> null }
        if (rate == null || cohort.size <= 0 || cohort.sizeState == "suppressed" || cohort.dayStates["day_$age"] in setOf("not_mature", "suppressed", "unsupported")) null
        else cohort.size to rate
    }
    val size = rows.sumOf { it.first.toLong() }
    return if (size == 0L) null else rows.sumOf { it.first * it.second } / size
}

internal fun computeVitalsNumbers(retention: RetentionResponse?): VitalsNumbers? =
    retention?.let { VitalsNumbers(weightedRetentionRate(it, 1)) }

/** Only compare values with the same collection semantics. */
internal fun comparableDuration(current: OverviewDurationValue, previous: OverviewDurationValue): Int? =
    previous.seconds.takeIf { current.seconds != null && current.source == previous.source }
