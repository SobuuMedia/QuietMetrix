package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The dynamic pieces the Overview hero's "AI-written" sentence is built from, derived entirely
 * from aggregate/session data the dashboard has already fetched — see [computeHeroNumbers].
 *
 * Known limitation, tracked for a future backend task: the design handoff's Overview section
 * ("people" = distinct devices/WAU, see `design_handoff_dashboard_redesign/QuietMetrix
 * Prototype.dc.html:778`) and its mock headline data (same file, line ~931: hand-written
 * `people`/`prevPeople` per range, not derived from any real aggregation) describe metrics this
 * dashboard does not track server-side today — there is no distinct-device count and no
 * previous-period comparison anywhere in [AggregatesResponse]/[SessionsResponse]. [peopleCount]
 * and [percentChange] below are honest, already-available proxies, not that literal metric.
 */
internal data class HeroNumbers(
    /** Closest already-tracked proxy for "people": total sessions in the window. */
    val peopleCount: Long,
    /** Absolute percent difference between the two halves of the loaded daily buckets, or null
     *  when there isn't enough data (fewer than 2 buckets, or the first half sums to zero). This
     *  is a same-window momentum signal, not a true period-over-period comparison. */
    val percentChange: Int?,
    /** True when the second half of the window is >= the first (an increase or flat). Only
     *  meaningful when [percentChange] is non-null. */
    val percentIsIncrease: Boolean,
    /** The project's top event name for the period, or null when there is none. */
    val standoutEvent: String?,
)

/**
 * Derives [HeroNumbers] from the currently loaded [agg]/[sess] responses, or null when neither
 * has loaded yet (the hero has nothing to show — the Loading/Empty states take over in
 * [OverviewHero]).
 */
internal fun computeHeroNumbers(agg: AggregatesResponse?, sess: SessionsResponse?): HeroNumbers? {
    if (agg == null && sess == null) return null
    val peopleCount = sess?.totalSessions?.toLong() ?: agg?.totals?.events ?: 0L
    val daily = agg?.daily.orEmpty().map { it.total }
    val (percentChange, isIncrease) = momentumFrom(daily)
    val standout = agg?.topEvents?.firstOrNull()?.eventName
    return HeroNumbers(peopleCount, percentChange, isIncrease, standout)
}

/**
 * Splits [totals] into its first and second halves (the second half gets the extra element on an
 * odd-sized list) and compares their sums: the absolute rounded percent difference, and whether
 * the second half is greater than or equal to the first. Null percent when there are fewer than 2
 * buckets to compare, or the first half sums to zero (no base to compute a percentage against).
 */
private fun momentumFrom(totals: List<Long>): Pair<Int?, Boolean> {
    if (totals.size < 2) return null to true
    val half = totals.size / 2
    val first = totals.take(half).sum()
    val second = totals.drop(half).sum()
    if (first <= 0L) return null to true
    val diff = (second - first) * 100.0 / first
    return abs(diff.roundToInt()) to (diff >= 0)
}
