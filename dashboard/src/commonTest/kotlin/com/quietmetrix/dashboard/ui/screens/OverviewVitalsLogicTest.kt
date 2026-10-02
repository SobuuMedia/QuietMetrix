package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.DailyPoint
import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.Totals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverviewVitalsLogicTest {

    @Test
    fun nullWhenNothingHasLoaded() {
        assertNull(computeVitalsNumbers(null, null, null))
    }

    @Test
    fun sessionCountPrefersSessionTotalOverEventTotal() {
        val agg = AggregatesResponse(totals = Totals(events = 500))
        val sess = SessionsResponse(totalSessions = 128)
        assertEquals(128L, computeVitalsNumbers(agg, sess, null)?.sessionCount)
    }

    @Test
    fun sessionCountFallsBackToEventTotalWhenNoSessionsResponse() {
        val agg = AggregatesResponse(totals = Totals(events = 500))
        assertEquals(500L, computeVitalsNumbers(agg, null, null)?.sessionCount)
    }

    @Test
    fun avgDurationComesStraightFromSessionsResponse() {
        val sess = SessionsResponse(avgDurationSec = 185)
        assertEquals(185, computeVitalsNumbers(null, sess, null)?.avgDurationSec)
    }

    @Test
    fun avgDurationIsZeroWhenNoSessionsResponse() {
        val agg = AggregatesResponse(totals = Totals(events = 10))
        assertEquals(0, computeVitalsNumbers(agg, null, null)?.avgDurationSec)
    }

    @Test
    fun errorsCountComesStraightFromAggregatesTotals() {
        val agg = AggregatesResponse(totals = Totals(errors = 42))
        assertEquals(42L, computeVitalsNumbers(agg, null, null)?.errorsCount)
    }

    @Test
    fun sessionTrendNullWithFewerThanTwoDailySessionBuckets() {
        val sess = SessionsResponse(dailySessions = listOf(DailyPoint(day = "d1", total = 10)))
        assertNull(computeVitalsNumbers(null, sess, null)?.sessionTrendChange)
    }

    @Test
    fun sessionTrendNullWhenFirstHalfSumsToZero() {
        val sess = SessionsResponse(
            dailySessions = listOf(
                DailyPoint(day = "d1", total = 0),
                DailyPoint(day = "d2", total = 0),
                DailyPoint(day = "d3", total = 50),
            ),
        )
        assertNull(computeVitalsNumbers(null, sess, null)?.sessionTrendChange)
    }

    @Test
    fun sessionTrendDetectsAnIncrease() {
        // first half (d1,d2) sums 100, second half (d3,d4) sums 150 -> +50%.
        val sess = SessionsResponse(
            dailySessions = listOf(
                DailyPoint(day = "d1", total = 50),
                DailyPoint(day = "d2", total = 50),
                DailyPoint(day = "d3", total = 70),
                DailyPoint(day = "d4", total = 80),
            ),
        )
        val vitals = computeVitalsNumbers(null, sess, null)
        assertEquals(50, vitals?.sessionTrendChange)
        assertTrue(vitals!!.sessionTrendIsIncrease)
    }

    @Test
    fun sessionTrendDetectsADecrease() {
        // first half (d1,d2) sums 150, second half (d3,d4) sums 100 -> -33%.
        val sess = SessionsResponse(
            dailySessions = listOf(
                DailyPoint(day = "d1", total = 80),
                DailyPoint(day = "d2", total = 70),
                DailyPoint(day = "d3", total = 50),
                DailyPoint(day = "d4", total = 50),
            ),
        )
        val vitals = computeVitalsNumbers(null, sess, null)
        assertEquals(33, vitals?.sessionTrendChange)
        assertFalse(vitals!!.sessionTrendIsIncrease)
    }

    @Test
    fun retentionDay1IsNullWhenNoRetentionResponseLoaded() {
        val agg = AggregatesResponse(totals = Totals(events = 10))
        assertNull(computeVitalsNumbers(agg, null, null)?.retentionDay1)
    }

    @Test
    fun retentionDay1IsNullWhenNoCohortHasObservedIt() {
        val retention = RetentionResponse(
            cohorts = listOf(
                RetentionCohort(cohortDate = "2026-01-01", size = 10, day1 = null),
                RetentionCohort(cohortDate = "2026-01-02", size = 0, day1 = 0.5),
            ),
        )
        assertNull(computeVitalsNumbers(null, null, retention)?.retentionDay1)
    }

    @Test
    fun retentionDay1PicksTheMostRecentObservedCohort() {
        val retention = RetentionResponse(
            cohorts = listOf(
                RetentionCohort(cohortDate = "2026-01-01", size = 10, day1 = 0.4),
                RetentionCohort(cohortDate = "2026-01-03", size = 12, day1 = 0.6),
                RetentionCohort(cohortDate = "2026-01-02", size = 11, day1 = 0.5),
            ),
        )
        assertEquals(0.6, computeVitalsNumbers(null, null, retention)?.retentionDay1)
    }

    @Test
    fun retentionDay1SkipsUnobservedCohortsEvenIfMoreRecent() {
        val retention = RetentionResponse(
            cohorts = listOf(
                RetentionCohort(cohortDate = "2026-01-01", size = 10, day1 = 0.4),
                // Most recent cohort hasn't aged into day-1 observability yet.
                RetentionCohort(cohortDate = "2026-01-05", size = 10, day1 = null),
            ),
        )
        assertEquals(0.4, computeVitalsNumbers(null, null, retention)?.retentionDay1)
    }
}
