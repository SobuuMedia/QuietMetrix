package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.SessionsResponse

/** Real headline inputs only; People is never inferred from sessions or event totals. */
internal data class HeroNumbers(
    val peopleCount: Long?,
    val hasActivity: Boolean,
    val changePercent: Int?,
    val standoutEvent: String?,
)

/**
 * Uses the backend's exact fixed-window People measure and its prior-period People measure,
 * falling back to a separately loaded equal-length legacy window when available. When either is
 * unavailable, the UI uses neutral copy instead of substituting sessions or event totals.
 */
internal fun computeHeroNumbers(
    agg: AggregatesResponse?,
    sess: SessionsResponse?,
    previous: AggregatesResponse? = null,
): HeroNumbers? {
    if (agg == null && sess == null) return null
    val currentPeople = agg?.people
    val previousPeople = agg?.peopleComparison ?: previous?.people
    val change = if (currentPeople != null && previousPeople != null && previousPeople > 0L) {
        (((currentPeople - previousPeople).toDouble() / previousPeople.toDouble()) * 100.0).toInt()
    } else null
    val eventName = agg?.topEvents?.firstOrNull()?.eventName
        ?.replace('_', ' ')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    return HeroNumbers(
        peopleCount = currentPeople,
        hasActivity = (sess?.totalSessions ?: 0) > 0 || (agg?.totals?.events ?: 0L) > 0,
        changePercent = change,
        standoutEvent = eventName,
    )
}
