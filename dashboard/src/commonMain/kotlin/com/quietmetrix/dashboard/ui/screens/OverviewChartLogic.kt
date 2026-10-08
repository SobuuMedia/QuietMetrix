package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.ReportBinDto
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.format.formatBucketLabel

/**
 * Which real data series the Task 20 daily-volume chart card's segmented control can show.
 *
 * People uses distinct-installation report bins; Visits uses session counts; Actions uses event
 * counts. The People series is already privacy-gated by the report API, and unavailable or
 * suppressed bins stay absent instead of becoming invented zeroes.
 */
enum class DailyVolumeMetric { People, Visits, Actions }

/** One rendered bar: an x-axis label plus its real count for the selected [DailyVolumeMetric]. */
data class DailyVolumeBar(val label: String, val value: Long)

/**
 * Builds the daily-volume chart's bar series for [metric] from real data only:
 * [DailyVolumeMetric.People] maps to the privacy-gated `AggregatesResponse.dailyPeople` series,
 * [DailyVolumeMetric.Visits] maps to [SessionsResponse.dailySessions] (session counts per bucket,
 * the same series `OverviewVitalsLogic`'s session trend already reads), and
 * [DailyVolumeMetric.Actions] maps to [AggregatesResponse.daily] (event counts per bucket, the
 * pre-redesign "Daily volume" card's exact data source). Bucket labels are formatted with the
 * existing [formatBucketLabel] (hourly "14:00" vs. daily "2026-06-28"), matching every other chart
 * on this screen.
 */
fun dailyVolumeBars(
    agg: AggregatesResponse?,
    sess: SessionsResponse?,
    metric: DailyVolumeMetric,
    hourly: Boolean,
    people: List<com.quietmetrix.dashboard.api.DailyPoint> = agg?.dailyPeople.orEmpty(),
): List<DailyVolumeBar> = when (metric) {
    DailyVolumeMetric.People -> people.map { DailyVolumeBar(formatBucketLabel(it.day, hourly), it.total) }
    DailyVolumeMetric.Visits -> (sess?.dailySessions ?: emptyList())
        .map { DailyVolumeBar(formatBucketLabel(it.day, hourly), it.total) }
    DailyVolumeMetric.Actions -> (agg?.daily ?: emptyList())
        .map { DailyVolumeBar(formatBucketLabel(it.day, hourly), it.total) }
}

/** Converts report chart bins to points while preserving hourly keys and omitting suppressed bins. */
fun List<ReportBinDto>.chartMeasurePoints(measureKey: String): List<com.quietmetrix.dashboard.api.DailyPoint> = mapNotNull { bin ->
    val measure = bin.measures[measureKey] ?: return@mapNotNull null
    if (measure.state !in setOf("reported", "zero")) return@mapNotNull null
    val key = bin.hour?.let { "${bin.startDay}T${it.toString().padStart(2, '0')}" } ?: bin.startDay
    com.quietmetrix.dashboard.api.DailyPoint(day = key, total = measure.value ?: 0L)
}

/**
 * Index of the real "record day" (the bar with the strictly highest, uniquely-held value) to
 * highlight with a lime bar — the one part of the README's "record day ... floating '🚀 v2.3
 * shipped' label and a lime bar" feature that is honestly derivable from real data (see
 * `DailyVolumeSection`'s KDoc for why the literal "v2.3 shipped" caption text is NOT rendered: it
 * is placeholder flavor text from the design mockup, not backed by any changelog/deploy-marker
 * concept anywhere in this backend, and showing it verbatim to a real user would fabricate an
 * event that didn't happen for them).
 *
 * Returns `null` when there is nothing honest to call out: an empty series, a series where every
 * value is zero (no activity at all), or a series with 2+ bars where every value ties at the same
 * maximum (no single day stands out) — in all three cases, singling out one bar as "the busiest
 * day" would overclaim a pattern the data doesn't show. A single-bar series is never "tied" with
 * another day, so its one value (if positive) is honestly reported as the record.
 */
fun recordDayIndex(bars: List<DailyVolumeBar>): Int? {
    if (bars.isEmpty()) return null
    val maxValue = bars.maxOf { it.value }
    if (maxValue <= 0L) return null
    if (bars.size > 1 && bars.all { it.value == maxValue }) return null
    return bars.indexOfFirst { it.value == maxValue }
}

/** Aligns sparse previous day totals by calendar distance, preserving absent buckets as null. */
internal fun comparisonDailyValues(current: List<com.quietmetrix.dashboard.api.DailyPoint>, previous: List<com.quietmetrix.dashboard.api.DailyPoint>, days: Int): List<Float?> {
    val byDay = previous.mapNotNull { point -> epochDayFromIsoOrNull(point.day)?.let { it to point.total.toFloat() } }.toMap()
    return current.map { point -> epochDayFromIsoOrNull(point.day)?.let { byDay[it - days] } }
}

/** Keeps native daily resolution; long windows group measured days into aligned seven-day blocks. */
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun analyticsChartPoints(points: List<com.quietmetrix.dashboard.api.DailyPoint>, range: com.quietmetrix.dashboard.api.TimeRange, anchor: String, previous: Boolean = false): List<com.quietmetrix.dashboard.api.DailyPoint> {
    if (range != com.quietmetrix.dashboard.api.TimeRange.Day90) return points.sortedBy { it.day }
    val end = epochDayFromIsoOrNull(anchor) ?: return points
    val days = ((range.seconds + 86399) / 86400).coerceAtLeast(1)
    val start = end - days + 1 - (if (previous) days else 0)
    return points.mapNotNull { point -> epochDayFromIsoOrNull(point.day)?.let { day -> (start + (day - start).floorDiv(7) * 7) to point } }
        .groupBy({ it.first }, { it.second }).entries.sortedBy { it.key }.map { (day, values) ->
            com.quietmetrix.dashboard.api.DailyPoint(kotlin.time.Instant.fromEpochSeconds(day * 86400).toString().take(10), values.sumOf { it.total })
        }
}

/** Explicit inclusive UTC dates explain what daily counters mean for a selected range. */
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun analyticsPeriodDates(range: com.quietmetrix.dashboard.api.TimeRange, anchor: String): List<String> {
    val end = epochDayFromIsoOrNull(anchor) ?: return List(4) { anchor }
    val days = ((range.seconds + 86399) / 86400).coerceAtLeast(1)
    fun date(day: Long) = kotlin.time.Instant.fromEpochSeconds(day * 86400).toString().take(10)
    return listOf(date(end - days + 1), date(end), date(end - 2 * days + 1), date(end - days))
}
