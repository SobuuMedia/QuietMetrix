package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.nav.NavDestination

internal fun aiSelectionContext(state: DashboardState): Map<String, String> = buildMap {
    put("screen", state.activeDestination.name)
    val filter = state.reportFilter()
    put("filter_kind", filter.kind); put("filter_value", filter.value)
    if (state.activeDestination == NavDestination.Flow) state.flowNode?.let { put("node", it) }
    if (state.activeDestination == NavDestination.Funnels) {
        state.selectedFunnelKey?.let { put("funnel", it) }
        state.funnels.firstOrNull { it.funnelKey == state.selectedFunnelKey }?.let { put("revision", (state.selectedFunnelRevision ?: it.revision).toString()) }
    }
    if (state.activeDestination == NavDestination.Retention) state.selectedCohorts?.let { put("cohort", it) }
    if (state.activeDestination == NavDestination.Experiments) state.selectedExperimentKey?.let { put("experiment", it) }
    putAll(state.askContext)
}
