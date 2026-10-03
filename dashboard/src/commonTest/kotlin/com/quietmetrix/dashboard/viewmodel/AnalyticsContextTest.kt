package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.TimeRange
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards against late analytics responses and comparisons crossing project/window selections. */
class AnalyticsContextTest {
    @Test
    fun oldSelectionsCannotCommitTheirResponses() {
        val state = DashboardState(currentProjectId = "proj_1", range = TimeRange.Day7)
        val context = analyticsContext(state)
        assertTrue(state.matchesAnalyticsContext(context))
        assertFalse(state.copy(currentProjectId = "proj_2").matchesAnalyticsContext(context))
        assertFalse(state.copy(range = TimeRange.Day30).matchesAnalyticsContext(context))
        assertFalse(state.copy(compare = true).matchesAnalyticsContext(context))
        assertFalse(state.copy(analyticsRevision = state.analyticsRevision + 1).matchesAnalyticsContext(context))
    }
    @Test
    fun backgroundManagementFetchDoesNotBlankAnAnalyticsScreen() {
        val state = DashboardState(loadingSections = setOf(DataSection.Users, DataSection.Tokens))
        assertFalse(state.isLoading(DataSection.Sessions))
        assertTrue(state.isLoading(DataSection.Users))
        assertTrue(state.copy(loading = true).isLoading(DataSection.Retention))
    }

    @Test
    fun everyAnalyticsDestinationUsesItsOwnComparisonSections() {
        val destinations = listOf(com.quietmetrix.dashboard.nav.NavDestination.Overview, com.quietmetrix.dashboard.nav.NavDestination.Flow,
            com.quietmetrix.dashboard.nav.NavDestination.Retention, com.quietmetrix.dashboard.nav.NavDestination.Funnels,
            com.quietmetrix.dashboard.nav.NavDestination.Experiments)
        destinations.forEach { assertTrue(analyticsSections(it).isNotEmpty()) }
        assertTrue(analyticsSections(com.quietmetrix.dashboard.nav.NavDestination.Projects).isEmpty())
        assertFalse(DataSection.ExperimentResults in analyticsSections(com.quietmetrix.dashboard.nav.NavDestination.Retention))
    }
}
