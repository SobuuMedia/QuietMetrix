package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
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
 * but it DOES find a genuine ambiguity in how to *read* a null value here, fixed by
 * [effectiveDayValue] below (post-review correction, see that function's KDoc for the full
 * writeup): a null [com.quietmetrix.dashboard.api.RetentionCohort.day1]/`.day7`/`.day30` is
 * produced by the identical code path for two different real-world states — "no cohort is old
 * enough yet" (temporal) and "a cohort IS old enough, but genuinely nobody returned" (a real 0%) —
 * and [RetentionCohort.cohortDate] must be consulted against the current date to tell them apart,
 * since [RetentionCounterAnalyzer.analyze] itself only ever emits a bare ratio or nothing, never an
 * explicit "zero, confirmed."
 */
internal data class RetentionVitals(
    /** Day-1 retention fraction (0..1) of the most recent cohort old enough to have observed it
     *  (including a genuine 0% from an aged-out cohort — see [effectiveDayValue]), or null when no
     *  cohort has reached that age yet. */
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
 * to a non-null fraction for [dayValue] at [dayNumber] days — see that function's KDoc for what
 * "resolves" means (a real observed ratio, or a confirmed 0% from a cohort old enough to have aged
 * out with nobody returning; never a cohort that's simply too young to tell).
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
 * Resolves what a cohort's raw day-N field ([rawValue]) actually means, disambiguating the one
 * case [RetentionCounterAnalyzer.analyze] leaves ambiguous: **post-review correction** — an
 * earlier version of this file treated `rawValue == null` as always meaning "too early to tell",
 * which is only true for a cohort that hasn't reached day N yet. A cohort that HAS reached day N
 * but where literally nobody returned produces the IDENTICAL `null` (the server only ever counts a
 * returning device; it never writes an explicit "0 returned" cell), so the original code silently
 * mislabeled a genuine 0% as "unknown" — a real risk once this extends from D1 (one churned device
 * rarely IS the whole cohort) to D7/D30, where a small cohort hitting a true 0% is plausible, not
 * theoretical (the design handoff's own "out of 10 people" framing invites exactly this).
 *
 * The fix: a zero-size cohort has nothing to measure and always resolves to null (mirroring
 * [com.quietmetrix.dashboard.api.RetentionCohort.day1]'s own "null, not zero" convention). For a
 * cohort with people in it, a non-null [rawValue] is returned as-is (a real observed ratio).
 * Otherwise [RetentionCohort.cohortDate] is parsed and compared against [todayEpochDay]: if the
 * cohort is at least [dayNumber] days old, it has genuinely had its chance and `0.0` is returned (a
 * real, confirmed zero); if it's younger than that — or its date fails to parse — null is returned,
 * honestly meaning "can't tell yet."
 */
private fun effectiveDayValue(cohort: RetentionCohort, dayNumber: Int, rawValue: Double?, todayEpochDay: Long): Double? {
    if (cohort.size <= 0) return null
    if (rawValue != null) return rawValue
    val cohortEpochDay = epochDayFromIsoOrNull(cohort.cohortDate) ?: return null
    val ageInDays = todayEpochDay - cohortEpochDay
    return if (ageInDays >= dayNumber) 0.0 else null
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
