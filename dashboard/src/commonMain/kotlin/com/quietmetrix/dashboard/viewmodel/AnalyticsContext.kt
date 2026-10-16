package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.ExperimentResultsResponse
import com.quietmetrix.dashboard.api.FrictionResponse
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.api.SearchResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.TransitionsResponse
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Identity of one project/window selection used to reject stale asynchronous responses. */
internal data class AnalyticsContext(
    val projectId: String?, val range: TimeRange, val compare: Boolean, val anchor: String,
    val filter: OverviewFilter?, val revision: Long, val demo: Boolean, val funnelKey: String?, val funnelRevision: Int?, val experimentKey: String?,
)

/** Previous-period responses remain separate from the current statistics used for actions. */
data class ComparisonData(
    val aggregates: AggregatesResponse? = null,
    val sessions: SessionsResponse? = null,
    val transitions: TransitionsResponse? = null,
    val search: SearchResponse? = null,
    val friction: FrictionResponse? = null,
    val retention: RetentionResponse? = null,
    val funnel: FunnelResultsResponse? = null,
    val experiment: ExperimentResultsResponse? = null,
)

/** Returns today's UTC date without introducing a date-library dependency. */
@OptIn(ExperimentalTime::class)
internal fun analyticsAnchorToday(): String = Clock.System.now().toString().take(10)

/** Captures the controls that define an analytics request. */
internal fun analyticsContext(state: DashboardState): AnalyticsContext = AnalyticsContext(
    state.currentProjectId, state.range, state.compare, state.analyticsAnchor, state.reportFilter(), state.analyticsRevision,
    state.demoMode, state.selectedFunnelKey, state.selectedFunnelRevision, state.selectedExperimentKey,
)

/** Allows a response to commit only while its original controls are still selected. */
internal fun DashboardState.matchesAnalyticsContext(context: AnalyticsContext): Boolean =
    analyticsContext(this) == context

/** A section uses startup loading or its own live request, rather than an unrelated tab's request. */
internal fun DashboardState.isLoading(vararg sections: DataSection): Boolean = loading || sections.any { it in loadingSections }

/** Analytics sections shown by each destination; management data has no date-window comparison. */
internal fun analyticsSections(destination: com.quietmetrix.dashboard.nav.NavDestination): Set<DataSection> = when (destination) {
    com.quietmetrix.dashboard.nav.NavDestination.Overview -> setOf(DataSection.Aggregates, DataSection.Sessions, DataSection.Retention)
    com.quietmetrix.dashboard.nav.NavDestination.Flow -> setOf(DataSection.Aggregates, DataSection.Transitions, DataSection.Search, DataSection.Friction)
    com.quietmetrix.dashboard.nav.NavDestination.Retention -> setOf(DataSection.Retention)
    com.quietmetrix.dashboard.nav.NavDestination.Funnels -> setOf(DataSection.FunnelResults)
    com.quietmetrix.dashboard.nav.NavDestination.Experiments -> setOf(DataSection.ExperimentResults)
    else -> emptySet()
}

/** One selected stored segment; no approximate scaling or unsupported intersections. */
data class OverviewFilter(val kind: String, val value: String)
internal fun DashboardState.reportFilter(): OverviewFilter =
    if (activeDestination == com.quietmetrix.dashboard.nav.NavDestination.Overview) overviewFilter ?: OverviewFilter("platform", "all")
    else OverviewFilter("platform", if (activeDestination == com.quietmetrix.dashboard.nav.NavDestination.Flow) flowPlatform else "all")
