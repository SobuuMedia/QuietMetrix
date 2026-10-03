package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.ui.components.CohortGrouping
import com.quietmetrix.dashboard.ui.components.buildCohortHeatmapRows
import kotlin.test.Test
import kotlin.test.assertEquals

/** Real SDK cohort labels must survive date parsing and the default heatmap grouping. */
class ReportedCohortTest {
    @Test
    fun sdkYearWeekBinsAreNotAssumedToBeIsoWeeks() {
        assertEquals(epochDayFromIsoOrNull("2026-01-01"), epochDayFromIsoOrNull("2026-W01"))
        assertEquals(epochDayFromIsoOrNull("2026-01-08"), epochDayFromIsoOrNull("2026-W02"))
        val rows = buildCohortHeatmapRows(listOf(RetentionCohort("2026-W01", 20, day1 = .5)), CohortGrouping.ByWeek, epochDayFromIsoOrNull("2026-02-01")!!)
        assertEquals(1, rows.size)
        assertEquals(.5, rows.single().cells.first().fraction)
    }
}
