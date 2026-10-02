package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
import kotlin.math.roundToInt

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
 * `day7`/`day30` for the Retention screen's own 3-card row, and finds no gap: a null value here
 * means "no cohort has reached that age yet" (or no cohort data at all), never a missing
 * implementation.
 */
internal data class RetentionVitals(
    /** Day-1 retention fraction (0..1) of the most recent cohort old enough to have observed it, or
     *  null when none has. */
    val day1: Double?,
    /** Day-7 retention fraction (0..1) of the most recent cohort old enough to have observed it, or
     *  null when none has. */
    val day7: Double?,
    /** Day-30 retention fraction (0..1) of the most recent cohort old enough to have observed it, or
     *  null when none has. */
    val day30: Double?,
)

/**
 * Derives [RetentionVitals] from [response]'s cohorts. Each of day1/day7/day30 is resolved
 * independently (not all three necessarily come from the same cohort row): a newly-installed
 * cohort might already have a `day1` figure while the only cohort old enough to show `day30` is a
 * much earlier one — same "most recent cohort that has actually observed this day" selection
 * [OverviewVitalsLogic.kt]'s `computeVitalsNumbers` already established for `day1` alone (Task 19),
 * generalized here to all three headline days.
 */
internal fun computeRetentionVitals(response: RetentionResponse?): RetentionVitals {
    val cohorts = response?.cohorts.orEmpty()
    return RetentionVitals(
        day1 = mostRecentObserved(cohorts) { it.day1 },
        day7 = mostRecentObserved(cohorts) { it.day7 },
        day30 = mostRecentObserved(cohorts) { it.day30 },
    )
}

/**
 * The most recent (by [RetentionCohort.cohortDate]) cohort for which [dayValue] is non-null and
 * the cohort has a positive [RetentionCohort.size] — mirroring
 * [com.quietmetrix.dashboard.api.RetentionCohort.day1]'s own "null, not zero" convention: a
 * zero-size cohort has nothing to compute a ratio from, so it's excluded rather than treated as an
 * honest 0%.
 */
private fun mostRecentObserved(
    cohorts: List<RetentionCohort>,
    dayValue: (RetentionCohort) -> Double?,
): Double? =
    cohorts
        .filter { dayValue(it) != null && it.size > 0 }
        .maxByOrNull { it.cohortDate }
        ?.let(dayValue)

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
