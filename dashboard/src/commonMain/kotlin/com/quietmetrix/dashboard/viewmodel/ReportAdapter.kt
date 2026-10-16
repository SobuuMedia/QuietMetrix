package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.ProjectReportDto
import com.quietmetrix.dashboard.ui.screens.chartMeasurePoints

/** Current and previous values share the same v2 definitions and projection. */
internal fun ProjectReportDto.toAggregates(legacy: AggregatesResponse, previous: Boolean): AggregatesResponse {
    val measures = if (previous) comparison else overview
    val bins = if (previous) comparisonCharts else charts
    return legacy.copy(
        people = measures["people"]?.value, peopleState = measures["people"]?.state, peopleSource = measures["people"]?.source,
        peopleComparison = if (previous) null else comparison["people"]?.value,
        v2Visits = measures["visits"]?.value,
        v2VisitDurationMs = measures["visit_duration"]?.value, v2VisitDurationState = measures["visit_duration"]?.state,
        v2Crashes = measures["crashes"]?.value, v2CrashesState = measures["crashes"]?.state,
        dailyPeople = bins["people"].orEmpty().chartMeasurePoints("people"),
        dailyPeoplePrevious = comparisonCharts["people"].orEmpty().chartMeasurePoints("people"),
        dailyVisitsV2 = bins["visits"].orEmpty().chartMeasurePoints("visits"),
        dailyVisitsV2Previous = comparisonCharts["visits"].orEmpty().chartMeasurePoints("visits"),
        dailyActionsV2 = bins["events"].orEmpty().chartMeasurePoints("events"),
        dailyActionsV2Previous = comparisonCharts["events"].orEmpty().chartMeasurePoints("events"),
    )
}
