package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The dynamic pieces the Overview hero's "AI-written" sentence is built from, derived entirely
 * from aggregate/session data the dashboard has already fetched — see [computeHeroNumbers].
 *
 * Naming note (post-review correction — see Task 18's report "Relabeling" section): the design
 * handoff's Overview section describes a "people" metric (distinct devices/WAU, see
 * `design_handoff_dashboard_redesign/QuietMetrix Prototype.dc.html:778`) that this dashboard does
 * not track anywhere server-side — there is no distinct-device count in
 * [AggregatesResponse]/[SessionsResponse]. An earlier version of this file called
 * [sessionCount] "peopleCount" and rendered it with the word "people"; a review correctly flagged
 * that as a real data-honesty problem (claiming a metric real developers would read as "unique
 * users" when it's actually total sessions), not a cosmetic one, and the fix was to relabel
 * honestly rather than keep the misleading wording with or without a caveat. [sessionCount] is
 * exactly what it says: total sessions in the window, rendered as "sessions" in the hero.
 *
 * Same reasoning applies to [momentumIsIncrease]: the design handoff's mock headline data
 * (`Prototype.dc.html:931`) hand-authors a `prevPeople` figure per range and presents a percent
 * delta as a real previous-period comparison — this dashboard has no previous-period data to
 * compare against at all (the API doesn't return a prior period's totals). [momentumIsIncrease]
 * is a same-window momentum signal (first half of the loaded daily buckets vs. the second half),
 * and per the same review decision it is rendered only as a direction ("trending up"/"trending
 * down this period"), never as a numeric percentage framed like a period-over-period change.
 */
internal data class HeroNumbers(
    /** Total sessions in the window — rendered in the hero as "N sessions", not "N people". */
    val sessionCount: Long,
    /** Null when there isn't enough data to judge a trend (fewer than 2 daily buckets, or the
     *  first half sums to zero — no base to compare against). Non-null otherwise, in which case
     *  [momentumIsIncrease] says which direction. */
    val momentumChange: Int?,
    /** True when the second half of the loaded daily buckets sums to >= the first half (an
     *  increase or flat trend). Only meaningful when [momentumChange] is non-null. Rendered as a
     *  trend direction only ("trending up"/"down this period") — never as the numeric magnitude,
     *  since this is not a real period-over-period comparison (see this class's KDoc). */
    val momentumIsIncrease: Boolean,
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
    val sessionCount = sess?.totalSessions?.toLong() ?: agg?.totals?.events ?: 0L
    val daily = agg?.daily.orEmpty().map { it.total }
    val (momentumChange, isIncrease) = momentumFrom(daily)
    val standout = agg?.topEvents?.firstOrNull()?.eventName
    return HeroNumbers(sessionCount, momentumChange, isIncrease, standout)
}

/**
 * Splits [totals] into its first and second halves (the second half gets the extra element on an
 * odd-sized list) and compares their sums: the absolute rounded percent difference, and whether
 * the second half is greater than or equal to the first. Null percent when there are fewer than 2
 * buckets to compare, or the first half sums to zero (no base to compute a percentage against).
 * The magnitude is kept for potential future/diagnostic use; [OverviewHero] only renders the
 * direction (see [HeroNumbers.momentumIsIncrease]'s KDoc for why).
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
