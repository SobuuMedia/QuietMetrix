package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The 4 Overview vitals cards' numbers, derived entirely from aggregate/session/retention data
 * the dashboard has already fetched — see [computeVitalsNumbers].
 *
 * Naming/honesty notes (same standard [HeroNumbers] established in Task 18 — see its KDoc):
 * - The design handoff's first vital is "People" (distinct devices/WAU,
 *   `design_handoff_dashboard_redesign/QuietMetrix Prototype.dc.html:778`) — a metric this
 *   dashboard does not track anywhere server-side. [sessionCount] is total sessions, the same
 *   proxy [HeroNumbers.sessionCount] uses, and is rendered labeled "Sessions"
 *   (`overview_vital_sessions_label`), never "People".
 * - The design handoff's fourth vital is "Crashes". The backend actually counts events named
 *   "error", OR "crash", OR "exception" together as one combined number
 *   (`servers/ktor/src/main/kotlin/com/quietmetrix/server/routes/DashboardRoutes.kt:135`,
 *   `php-hosting/src/routes/dashboard.php:57-58`) — [errorsCount] (= [AggregatesResponse.totals]
 *   `.errors`) is that combined count, not crashes alone. This card is rendered labeled "Errors"
 *   (reusing the existing `kpi_errors` string) with a caption disclosing the combined definition,
 *   rather than "Crashes", which would overclaim precision this dashboard doesn't have.
 * - [sessionTrendChange] is the ONLY delta this class computes. The other 3 vitals
 *   ([avgDurationSec], [retentionDay1], [errorsCount]) have no daily/historical breakdown
 *   anywhere in [AggregatesResponse]/[SessionsResponse]/[RetentionResponse] to honestly derive a
 *   trend from — rather than inventing one, [OverviewScreen]'s `VitalsSection` renders no delta
 *   pill at all for those 3 cards (see [KpiCard]'s own "omit to show no delta pill" contract).
 */
internal data class VitalsNumbers(
    /** Total sessions in the window — rendered as "N sessions", never "N people" (see this
     *  class's KDoc). */
    val sessionCount: Long,
    /** Null when there are fewer than 2 daily session buckets to compare, or the first half sums
     *  to zero (no base to compute a percentage against) — the ONLY vital with a delta pill; see
     *  this class's KDoc for why the other 3 fields never get one. */
    val sessionTrendChange: Int?,
    /** True when the second half of the daily session buckets sums to >= the first half. Only
     *  meaningful when [sessionTrendChange] is non-null. */
    val sessionTrendIsIncrease: Boolean,
    /** Average session duration in seconds ([SessionsResponse.avgDurationSec]) — directly
     *  tracked, not a proxy: this is exactly what the design handoff's "Time per visit" asks
     *  for. */
    val avgDurationSec: Int,
    /** Day-1 retention fraction (0..1) of the most recent cohort that has observed it (non-null
     *  [com.quietmetrix.dashboard.api.RetentionCohort.day1] and a positive cohort size), or null
     *  when no cohort has observed it yet — or when [RetentionResponse] itself hasn't loaded
     *  (see [computeVitalsNumbers]'s KDoc: the Overview screen doesn't eagerly fetch retention).
     *  Rendered as an honest "not tracked yet" state, never a fabricated 0%, matching
     *  [com.quietmetrix.dashboard.api.RetentionCohort.day1]'s own "null, not zero" convention. */
    val retentionDay1: Double?,
    /** Combined error/crash/exception event count for the window ([AggregatesResponse.totals]
     *  `.errors`) — see this class's KDoc for why the rendered label is "Errors", not "Crashes". */
    val errorsCount: Long,
)

/**
 * Derives [VitalsNumbers] from whatever of [agg]/[sess]/[retention] has loaded so far.
 *
 * [retention] is frequently null on the Overview screen: `DashboardViewModel.navigateTo` only
 * loads aggregates+sessions for `NavDestination.Overview` — retention is fetched lazily, only
 * when the user visits the Retention tab (`loadRetentionOnce()`). This function reads whatever
 * [retention] the caller already has in `DashboardState` (if a previous visit to Retention this
 * session already populated it) rather than fetching it itself, which is out of this file's
 * scope — see Task 19's report for this gap, tracked as a follow-up (wiring `loadRetentionOnce()`
 * into Overview's own load path so "Came back next day" has real data on first paint).
 *
 * Returns null only when none of the three has loaded yet (nothing at all to show).
 */
internal fun computeVitalsNumbers(
    agg: AggregatesResponse?,
    sess: SessionsResponse?,
    retention: RetentionResponse?,
): VitalsNumbers? {
    if (agg == null && sess == null && retention == null) return null
    val sessionCount = sess?.totalSessions?.toLong() ?: agg?.totals?.events ?: 0L
    val dailySessionTotals = sess?.dailySessions.orEmpty().map { it.total }
    val (trendChange, trendIsIncrease) = sessionMomentumFrom(dailySessionTotals)
    val day1 = retention?.cohorts.orEmpty()
        .filter { it.day1 != null && it.size > 0 }
        .maxByOrNull { it.cohortDate }
        ?.day1
    return VitalsNumbers(
        sessionCount = sessionCount,
        sessionTrendChange = trendChange,
        sessionTrendIsIncrease = trendIsIncrease,
        avgDurationSec = sess?.avgDurationSec ?: 0,
        retentionDay1 = day1,
        errorsCount = agg?.totals?.errors ?: 0L,
    )
}

/**
 * Same first-half/second-half-sum momentum technique as `OverviewHeroLogic.kt`'s private
 * `momentumFrom` (duplicated rather than shared across files to keep each screen-logic file
 * self-contained, same as that function operates on daily EVENT totals while this one operates on
 * daily SESSION counts — a different series). Null when there are fewer than 2 buckets, or the
 * first half sums to zero (no base to compare against).
 */
private fun sessionMomentumFrom(totals: List<Long>): Pair<Int?, Boolean> {
    if (totals.size < 2) return null to true
    val half = totals.size / 2
    val first = totals.take(half).sum()
    val second = totals.drop(half).sum()
    if (first <= 0L) return null to true
    val diff = (second - first) * 100.0 / first
    return abs(diff.roundToInt()) to (diff >= 0)
}
