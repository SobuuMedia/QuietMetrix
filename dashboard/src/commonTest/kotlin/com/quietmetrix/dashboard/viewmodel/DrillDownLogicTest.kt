package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.TopScreen
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.api.RetentionCohort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies drill-downs resolve only values already present in the filtered dashboard state. */
class DrillDownLogicTest {
    @Test
    fun visibleBreakdownValueIsSelected() {
        val state = DashboardState(aggregates = AggregatesResponse(
            topScreens = listOf(TopScreen(screen = "/home", count = 17)),
            platforms = listOf(BreakdownItem(name = "Android", count = 9)),
        ))
        assertEquals("17", selectDrillDown(state, "screen", "/home").value)
        assertEquals("9", selectDrillDown(state, "platform", "Android").value)
    }

    @Test
    fun unknownValueDoesNotInventANumber() {
        val result = selectDrillDown(DashboardState(), "screen", "/missing")
        assertEquals("—", result.value)
        assertEquals(false, result.available)
    }

    @Test
    fun retentionUsesTheSelectedMetricAndPreservesMissingValues() {
        val measured = DashboardState(retention = RetentionResponse(listOf(RetentionCohort("2026-W01", 20, day1 = .4, day7 = 0.0))))
        assertEquals("40%", selectDrillDown(measured, "retention", "day1").value)
        assertEquals("0%", selectDrillDown(measured, "retention", "day7").value)
        assertEquals("—", selectDrillDown(measured, "retention", "day30").value)
        assertEquals("—", selectDrillDown(measured, "retention", "0.0").value)
    }
    @Test
    fun selectedWeeklyBarResolvesTheSameCurrentAndPreviousTotalsAsTheChart() {
        val state = DashboardState(range = com.quietmetrix.dashboard.api.TimeRange.Day90, analyticsAnchor = "2026-10-03", compare = true,
            aggregates = AggregatesResponse(daily = listOf(com.quietmetrix.dashboard.api.DailyPoint("2026-07-06", 2), com.quietmetrix.dashboard.api.DailyPoint("2026-07-08", 3))),
            comparison = ComparisonData(aggregates = AggregatesResponse(daily = listOf(com.quietmetrix.dashboard.api.DailyPoint("2026-04-07", 9)))))
        val result = selectDrillDown(state, "day-actions", "2026-07-06")
        assertEquals("5", result.value)
        assertEquals("9", result.previousValue)
    }
    @Test
    fun cohortDrawersMatchThePreviousRowsShownByTheHeatmap() {
        val state = DashboardState(compare = true, retention = RetentionResponse(listOf(RetentionCohort("2026-W40", 120, day1 = .4))),
            comparison = ComparisonData(retention = RetentionResponse(listOf(RetentionCohort("2026-W39", 80, day1 = .5)))))
        val row = com.quietmetrix.dashboard.ui.components.buildCohortHeatmapRows(state.retention!!.cohorts, com.quietmetrix.dashboard.ui.components.CohortGrouping.ByWeek).single()
        assertEquals("80", selectDrillDown(state, "cohort-week", row.id).previousValue)
        assertEquals("80", selectDrillDown(state, "cohort", "2026-W40").previousValue)
    }
}
