package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverviewVitalsLogicTest {
    @Test
    fun `duration uses reported v2 and identifies legacy estimate`() {
        assertEquals(60, resolveOverviewDuration(60_500, "reported", 10).seconds)
        assertEquals(OverviewMeasureSource.ReportedV2, resolveOverviewDuration(60_500, "reported", 10).source)
        assertEquals(OverviewMeasureSource.LegacyApproximate, resolveOverviewDuration(null, "insufficient_data", 10).source)
    }

    @Test
    fun `suppressed v2 duration never falls back to legacy estimate`() {
        val value = resolveOverviewDuration(null, "suppressed", 10)
        assertNull(value.seconds)
        assertEquals(OverviewMeasureSource.Suppressed, value.source)
    }

    @Test
    fun `crashes render only when the v2 measure is reportable`() {
        assertEquals(3L, resolveOverviewCrashes(3, "reported").count)
        assertEquals(null, resolveOverviewCrashes(3, "suppressed").count)
        assertEquals(OverviewMeasureSource.Unavailable, resolveOverviewCrashes(null, "insufficient_data").source)
    }

    @Test
    fun retentionUnavailableWhenResponseIsMissing() {
        assertNull(computeVitalsNumbers(null))
    }

    @Test
    fun dayOneUnavailableWhenNoMatureReportableCohortExists() {
        val retention = RetentionResponse(cohorts = listOf(
            RetentionCohort(cohortDate = "2026-01-01", size = 10, day1 = null),
            RetentionCohort(cohortDate = "2026-01-02", size = 0, day1 = 0.5),
        ))
        assertNull(computeVitalsNumbers(retention)?.retentionDay1)
    }

    @Test
    fun picksMostRecentMatureReportableCohort() {
        val retention = RetentionResponse(cohorts = listOf(
            RetentionCohort(cohortDate = "2026-01-01", size = 10, day1 = 0.4),
            RetentionCohort(cohortDate = "2026-01-03", size = 12, day1 = 0.6),
            RetentionCohort(cohortDate = "2026-01-02", size = 11, day1 = 0.5),
        ))
        assertEquals(0.6, computeVitalsNumbers(retention)?.retentionDay1)
    }

    @Test
    fun ignoresMoreRecentUnmaturedCohortInsteadOfCallingItZero() {
        val retention = RetentionResponse(cohorts = listOf(
            RetentionCohort(cohortDate = "2026-01-01", size = 10, day1 = 0.4),
            RetentionCohort(cohortDate = "2026-01-05", size = 10, day1 = null),
        ))
        assertEquals(0.4, computeVitalsNumbers(retention)?.retentionDay1)
    }
}
