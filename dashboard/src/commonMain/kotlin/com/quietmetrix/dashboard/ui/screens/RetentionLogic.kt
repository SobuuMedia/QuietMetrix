package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.ui.components.COHORT_HEATMAP_DAY_NUMBERS
import com.quietmetrix.dashboard.ui.components.CohortHeatmapRow
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The Retention screen's 3 headline numbers (Day-1 / Day-7 / Day-30), each independently derived
 * from whichever cohort has most recently observed it — see [computeRetentionVitals].
 *
 * Data-honesty note (Task 24 investigation): all three fields are genuinely computed server-side
 * by the SAME mechanism, not a mix of real and placeholder data.
 * [com.quietmetrix.server.retention.RetentionCounterAnalyzer.analyze] (mirrored in
 * `php-hosting/src/retentionCounterAnalyze.php`) computes `day1`/`day3`/`day7`/`day14`/`day30` as
 * identical `n/size` ratios over the same persisted `retention{cohort, day}` counter cells — there
 * is no special-casing of day1 vs. the others anywhere in that pipeline. Task 19 had only verified
 * `day1` (the single field the Overview vitals grid needed); this task extends that verification to
 * `day7`/`day30` for the Retention screen's own 3-card row, and finds no gap in the computation —
 * But a missing ratio is unavailable: age cannot establish zero because the server applies
 * k-anonymity to return counts.
 */
internal data class RetentionVitals(
    /** Day-1 retention fraction (0..1) of the newest cohort with a visible server value, or null. */
    val day1: Double?,
    /** Day-7 retention fraction (0..1), same rules as [day1] at a 7-day threshold. */
    val day7: Double?,
    /** Day-30 retention fraction (0..1), same rules as [day1] at a 30-day threshold. */
    val day30: Double?,
)

/**
 * Derives [RetentionVitals] from [response]'s cohorts, as observed at [todayEpochDay] (days since
 * the Unix epoch, UTC — defaults to the real current day; a test passes a fixed value for
 * determinism). Each of day1/day7/day30 is resolved independently (not all three necessarily come
 * from the same cohort row): a newly-installed cohort might already have a `day1` figure while the
 * only cohort old enough to show `day30` is a much earlier one — same "most recent cohort that has
 * actually observed this day" selection [OverviewVitalsLogic.kt]'s `computeVitalsNumbers` already
 * established for `day1` alone (Task 19), generalized here to all three headline days, now reading
 * each cohort through [effectiveDayValue] rather than the raw field.
 */
@OptIn(ExperimentalTime::class)
internal fun computeRetentionVitals(
    response: RetentionResponse?,
    todayEpochDay: Long = Clock.System.now().toEpochMilliseconds().floorDiv(MILLIS_PER_DAY),
): RetentionVitals {
    val cohorts = response?.cohorts.orEmpty()
    return RetentionVitals(
        day1 = mostRecentObserved(cohorts, dayNumber = 1, todayEpochDay) { it.day1 },
        day7 = mostRecentObserved(cohorts, dayNumber = 7, todayEpochDay) { it.day7 },
        day30 = mostRecentObserved(cohorts, dayNumber = 30, todayEpochDay) { it.day30 },
    )
}

/** Milliseconds in a day — used to turn an [kotlin.time.Instant] into a whole epoch day. */
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The most recent (by [RetentionCohort.cohortDate]) cohort for which [effectiveDayValue] resolves
 * to a non-null fraction for [dayValue] at [dayNumber] days. Missing values remain unavailable.
 */
private fun mostRecentObserved(
    cohorts: List<RetentionCohort>,
    dayNumber: Int,
    todayEpochDay: Long,
    dayValue: (RetentionCohort) -> Double?,
): Double? =
    cohorts
        .mapNotNull { cohort -> effectiveDayValue(cohort, dayNumber, dayValue(cohort), todayEpochDay)?.let { cohort.cohortDate to it } }
        .maxByOrNull { it.first }
        ?.second

/**
 * Resolves a cohort's raw day-N field without inferring a measurement from age. The server applies
 * k-anonymity to return counts, so null can mean either a future observation or a suppressed count.
 *
 * A zero-size cohort has nothing to measure. For non-empty cohorts, preserve the server's raw
 * value: an explicit zero is a real measurement, while null stays unavailable because it may
 * represent either a future observation or a count suppressed by the privacy threshold.
 *
 * `internal` (not `private`) since Task 25's `CohortHeatmapLogic.kt` (`ui/components/`) reuses this
 * SAME raw-value handling per-cell across the cohort heatmap's (cohort × day) grid, rather than
 * reimplementing a similar-but-subtly-different version of it — the grid multiplies this exact
 * ambiguity across every cell, so one shared resolver is the only way both screens can agree on
 * what a given cell/card honestly means.
 */
internal fun effectiveDayValue(cohort: RetentionCohort, dayNumber: Int, rawValue: Double?, todayEpochDay: Long): Double? {
    if (cohort.size <= 0) return null
    return rawValue
}

/**
 * `"2026-08-01"` -> days since the Unix epoch, or null if [iso] isn't a well-formed `yyyy-MM-dd`
 * date. Dependency-free (no `kotlinx-datetime`): this project's own established precedent
 * (`quietmetrix-sdk`'s `EpochDay.kt`, see its file-level KDoc) is to compute this directly via
 * Howard Hinnant's `days_from_civil` algorithm rather than pull in a date library for one
 * conversion — duplicated here (not exposed across modules) since the SDK's version is `internal`
 * to a different Gradle module.
 */
internal fun epochDayFromIsoOrNull(iso: String): Long? {
    val parts = iso.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/**
 * How many of a 10-dot "people" grid are filled for a given retention [fraction] (0..1) — the
 * design handoff's friendliest-possible framing ("out of 10 people, how many stick around",
 * design_handoff_dashboard_redesign/README.md > Retention). Matches the HTML prototype's own
 * rounding (`QuietMetrix Prototype.dc.html:1092`'s `dots(n)` helper is fed a pre-rounded whole
 * number computed the same way elsewhere in that file): the fraction is scaled to [totalDots] and
 * rounded to the nearest whole dot, ties rounding up (e.g. 0.45 -> 4.5 -> 5).
 *
 * [fraction] is clamped to `[0, 1]` first so a rounding-artifact fraction fractionally outside that
 * range (e.g. `1.004`) still yields a sane dot count instead of `11` or `-1`.
 */
internal fun dotCountForFraction(fraction: Double, totalDots: Int = 10): Int =
    (fraction.coerceIn(0.0, 1.0) * totalDots).roundToInt().coerceIn(0, totalDots)

/**
 * Which variant of the Retention H1's plain-language sentence to render, based on whether the
 * Day-1 and/or Day-7 headline numbers are actually available (design handoff: "H1 states the
 * D1/D7 headline in plain words" — Day-30 is deliberately not part of the headline sentence,
 * matching the HTML prototype's own H1 markup, which only ever mentions "the next day" and "a
 * week later"). Kept as a separate pure enum/function (rather than inline `when` branches in the
 * composable) so the branching itself is unit-testable without a Compose host.
 */
internal enum class RetentionHeadlineKind { Both, Day1Only, Day7Only, NotEnoughData }

/** Picks a [RetentionHeadlineKind] from the two headline fractions — see that enum's KDoc. */
internal fun retentionHeadlineKind(day1: Double?, day7: Double?): RetentionHeadlineKind = when {
    day1 != null && day7 != null -> RetentionHeadlineKind.Both
    day1 != null -> RetentionHeadlineKind.Day1Only
    day7 != null -> RetentionHeadlineKind.Day7Only
    else -> RetentionHeadlineKind.NotEnoughData
}

// ---------------------------------------------------------------------------
// "⚖️ Comparing A vs B" panel + closing insight card — Task 26
// ---------------------------------------------------------------------------

/**
 * One (day-column) slot of an A-vs-B comparison. [aFraction]/[bFraction] are each already
 * honestly resolved by the caller (via [effectiveDayValue], never a raw/ambiguous field) — null
 * means that side genuinely hasn't reached [dayNumber] yet, exactly as [effectiveDayValue] defines
 * it, NEVER a confirmed zero's absence. [bothKnown] is false whenever either side is null, which is
 * the signal the UI uses to render an honest "not yet" placeholder for that single day-column
 * instead of a bar chart that would otherwise have to silently treat the unknown side as a
 * fabricated 0%.
 */
internal data class RetentionComparisonDayColumn(val dayNumber: Int, val aFraction: Double?, val bFraction: Double?) {
    val bothKnown: Boolean get() = aFraction != null && bFraction != null
}

/**
 * Builds one [RetentionComparisonDayColumn] per [COHORT_HEATMAP_DAY_NUMBERS] entry from the two
 * cohort-heatmap rows a user has ticked A/B for (Task 25's `toggleCohortHeatmapSelection`; this
 * function doesn't own that selection, only reads what it's given). [rowA]/[rowB]'s cells are
 * already [effectiveDayValue]-resolved by
 * [com.quietmetrix.dashboard.ui.components.buildCohortHeatmapRows], not re-derived here.
 */
internal fun computeRetentionComparisonDayColumns(rowA: CohortHeatmapRow, rowB: CohortHeatmapRow): List<RetentionComparisonDayColumn> =
    COHORT_HEATMAP_DAY_NUMBERS.mapIndexed { index, dayNumber ->
        RetentionComparisonDayColumn(dayNumber, rowA.cells.getOrNull(index)?.fraction, rowB.cells.getOrNull(index)?.fraction)
    }

/**
 * The "⚖️ Comparing A vs B" panel's one-line takeaway, or the closing insight card's cohort-over-
 * cohort trend sentence (same shape, different callers — see [computeRetentionComparisonTakeaway]
 * and [computeRetentionClosingInsightTakeaway]). [Comparison.deltaPoints] is [Comparison.bFraction]
 * minus [Comparison.aFraction] in whole percentage points, rounded — positive means B came back
 * more than A.
 */
internal sealed class RetentionComparisonTakeaway {
    data class Comparison(
        val dayNumber: Int,
        val aLabel: String,
        val aFraction: Double,
        val bLabel: String,
        val bFraction: Double,
    ) : RetentionComparisonTakeaway() {
        val deltaPoints: Int get() = ((bFraction - aFraction) * 100).roundToInt()
    }

    /** Neither group has a day-column both sides have actually observed yet — honestly say so
     *  rather than comparing a real number against an unobserved stand-in. */
    object NotEnoughData : RetentionComparisonTakeaway()
}

/**
 * Picks the [RetentionComparisonTakeaway] for two selected heatmap rows [rowA]/[rowB] (A/B per
 * Task 25's slot order — A is whichever row was ticked first, not necessarily the older one).
 * Mirrors the design handoff's own prototype rule verbatim (`QuietMetrix Prototype.dc.html:1121`):
 * among [COHORT_HEATMAP_DAY_NUMBERS], the LAST (highest-numbered, most-advanced) day-column where
 * BOTH rows resolve to a real fraction is the one the sentence is built from — never a day where
 * either side is still too young, exactly the prototype's own `cA[3][ci] != null && cB[3][ci] !=
 * null` guard, reused here via [RetentionComparisonDayColumn.bothKnown].
 */
internal fun computeRetentionComparisonTakeaway(rowA: CohortHeatmapRow, rowB: CohortHeatmapRow): RetentionComparisonTakeaway {
    val lastComparable = computeRetentionComparisonDayColumns(rowA, rowB).lastOrNull { it.bothKnown }
        ?: return RetentionComparisonTakeaway.NotEnoughData
    return RetentionComparisonTakeaway.Comparison(
        dayNumber = lastComparable.dayNumber,
        aLabel = rowA.label,
        aFraction = lastComparable.aFraction!!,
        bLabel = rowB.label,
        bFraction = lastComparable.bFraction!!,
    )
}

/**
 * The 5 cohort-age day-numbers the closing insight card compares across — duplicated from
 * [COHORT_HEATMAP_DAY_NUMBERS] rather than imported, matching this codebase's standing
 * per-file-duplication convention for small shared constants (see `CohortHeatmapLogic.kt`'s
 * `MILLIS_PER_DAY` KDoc for the same reasoning): [computeRetentionClosingInsightTakeaway] compares
 * raw [RetentionCohort]s directly, not [CohortHeatmapRow]s, so it has no actual dependency on the
 * heatmap's own constant.
 */
private val CLOSING_INSIGHT_DAY_NUMBERS: List<Int> = listOf(1, 3, 7, 14, 30)

/** [RetentionCohort]'s raw (possibly ambiguous — see [effectiveDayValue]) field for [dayNumber] —
 *  duplicated from `CohortHeatmapLogic.kt`'s private `rawDayValue` for the same per-file-
 *  duplication reason as [CLOSING_INSIGHT_DAY_NUMBERS]. */
private fun rawCohortDayValue(cohort: RetentionCohort, dayNumber: Int): Double? = when (dayNumber) {
    1 -> cohort.day1
    3 -> cohort.day3
    7 -> cohort.day7
    14 -> cohort.day14
    30 -> cohort.day30
    else -> null
}

/**
 * The Retention screen's closing insight card data: compares the most recent [RetentionCohort]
 * against the one immediately before it (by [RetentionCohort.cohortDate], descending — ISO dates
 * sort correctly lexically, matching `CohortHeatmapLogic.kt`'s own `sortedByDescending { it.id }`
 * convention) to see whether retention genuinely trended up or down, one real cohort-over-cohort
 * step at a time.
 *
 * **Data-honesty investigation (Task 26)**: the design handoff's own prototype hardcodes this
 * card's sentence verbatim ("People who scan a book on day one come back 2.3× more. Consider
 * showing the scanner in onboarding." — `QuietMetrix Prototype.dc.html:277`), a fabricated
 * feature-level causal claim with no backing field anywhere in [RetentionCohort]/[RetentionResponse]
 * (no per-feature/per-action breakdown exists on this response at all) — the same category of gap
 * Task 21's "Worth a look" investigation already found and refused to fabricate for Overview (see
 * that task's report: 3 hardcoded "insights" replaced with 3 plain, mechanically-derived facts).
 * What IS honestly reproducible from real data here is the prototype's OWN "Comparing A vs B"
 * sentence rule (same file, lines 1121-1123) applied automatically to the latest two cohorts
 * instead of a user's manual A/B tick — a real, mechanical "did retention move, and by how much"
 * fact, not an AI-shaped causal story. [computeRetentionComparisonTakeaway] already implements that
 * exact rule; this function just supplies it with the latest-vs-previous real cohort pair instead
 * of a user-selected one.
 *
 * Returns [RetentionComparisonTakeaway.NotEnoughData] both when fewer than 2 cohorts exist at all,
 * and when no single day-column has been observed by both the latest and the previous cohort —
 * same honesty rule as the A-vs-B panel: never fabricate a trend out of an unobserved comparison.
 */
@OptIn(ExperimentalTime::class)
internal fun computeRetentionClosingInsightTakeaway(
    cohorts: List<RetentionCohort>,
    todayEpochDay: Long = Clock.System.now().toEpochMilliseconds().floorDiv(MILLIS_PER_DAY),
): RetentionComparisonTakeaway {
    val sorted = cohorts.sortedByDescending { it.cohortDate }
    if (sorted.size < 2) return RetentionComparisonTakeaway.NotEnoughData
    val previous = sorted[1]
    val latest = sorted[0]
    fun resolved(cohort: RetentionCohort, dayNumber: Int) =
        effectiveDayValue(cohort, dayNumber, rawCohortDayValue(cohort, dayNumber), todayEpochDay)
    val lastComparableDay = CLOSING_INSIGHT_DAY_NUMBERS.lastOrNull { d -> resolved(previous, d) != null && resolved(latest, d) != null }
        ?: return RetentionComparisonTakeaway.NotEnoughData
    return RetentionComparisonTakeaway.Comparison(
        dayNumber = lastComparableDay,
        aLabel = previous.cohortDate,
        aFraction = resolved(previous, lastComparableDay)!!,
        bLabel = latest.cohortDate,
        bFraction = resolved(latest, lastComparableDay)!!,
    )
}
