package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Unit tests for [computeRetentionVitals], [dotCountForFraction] and [retentionHeadlineKind]. */
class RetentionLogicTest {

    // ---- computeRetentionVitals ----

    @Test
    fun allNullWhenResponseIsNull() {
        val vitals = computeRetentionVitals(null)
        assertNull(vitals.day1)
        assertNull(vitals.day7)
        assertNull(vitals.day30)
    }

    @Test
    fun allNullWhenNoCohorts() {
        val vitals = computeRetentionVitals(RetentionResponse(cohorts = emptyList()))
        assertNull(vitals.day1)
        assertNull(vitals.day7)
        assertNull(vitals.day30)
    }

    @Test
    fun picksTheMostRecentCohortThatHasObservedEachDayIndependently() {
        // The newest cohort (09-28) only has day1 so far; day7/day30 must fall back to the one
        // older cohort that has actually reached those ages, not silently stay null.
        val response = RetentionResponse(
            cohorts = listOf(
                RetentionCohort(cohortDate = "2026-08-01", size = 100, day1 = 0.30, day7 = 0.18, day30 = 0.08),
                RetentionCohort(cohortDate = "2026-09-28", size = 50, day1 = 0.40, day7 = null, day30 = null),
            ),
        )
        val vitals = computeRetentionVitals(response)
        assertEquals(0.40, vitals.day1)
        assertEquals(0.18, vitals.day7)
        assertEquals(0.08, vitals.day30)
    }

    @Test
    fun zeroSizeCohortIsExcludedEvenWithANonNullRatio() {
        val response = RetentionResponse(
            cohorts = listOf(RetentionCohort(cohortDate = "2026-09-01", size = 0, day1 = 0.0)),
        )
        assertNull(computeRetentionVitals(response).day1)
    }

    // ---- dotCountForFraction ----

    @Test
    fun zeroPercentIsZeroFilledDots() {
        assertEquals(0, dotCountForFraction(0.0))
    }

    @Test
    fun oneHundredPercentIsTenFilledDots() {
        assertEquals(10, dotCountForFraction(1.0))
    }

    @Test
    fun fortyTwoPercentRoundsDownToFourDots() {
        assertEquals(4, dotCountForFraction(0.42))
    }

    @Test
    fun fortyFivePercentRoundsUpToFiveDots() {
        // Exact tie (4.5) rounds up, matching kotlin.math.roundToInt's round-half-up behavior.
        assertEquals(5, dotCountForFraction(0.45))
    }

    @Test
    fun aFractionSlightlyAboveOneClampsToTenDots() {
        assertEquals(10, dotCountForFraction(1.004))
    }

    @Test
    fun aFractionSlightlyBelowZeroClampsToZeroDots() {
        assertEquals(0, dotCountForFraction(-0.001))
    }

    @Test
    fun aNonDefaultTotalDotsIsRespected() {
        assertEquals(3, dotCountForFraction(0.5, totalDots = 6))
    }

    // ---- retentionHeadlineKind ----

    @Test
    fun bothPresentWhenDay1AndDay7AreBothAvailable() {
        assertEquals(RetentionHeadlineKind.Both, retentionHeadlineKind(0.4, 0.2))
    }

    @Test
    fun day1OnlyWhenOnlyDay1IsAvailable() {
        assertEquals(RetentionHeadlineKind.Day1Only, retentionHeadlineKind(0.4, null))
    }

    @Test
    fun day7OnlyWhenOnlyDay7IsAvailable() {
        assertEquals(RetentionHeadlineKind.Day7Only, retentionHeadlineKind(null, 0.2))
    }

    @Test
    fun notEnoughDataWhenNeitherIsAvailable() {
        assertEquals(RetentionHeadlineKind.NotEnoughData, retentionHeadlineKind(null, null))
    }
}
