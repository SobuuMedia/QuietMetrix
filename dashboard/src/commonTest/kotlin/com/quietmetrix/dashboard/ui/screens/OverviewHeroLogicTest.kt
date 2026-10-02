package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.DailyPoint
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TopEvent
import com.quietmetrix.dashboard.api.Totals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverviewHeroLogicTest {

    @Test
    fun nullWhenNeitherResponseHasLoaded() {
        assertNull(computeHeroNumbers(null, null))
    }

    @Test
    fun sessionCountPrefersSessionTotalOverEventTotal() {
        val agg = AggregatesResponse(totals = Totals(events = 500))
        val sess = SessionsResponse(totalSessions = 128)
        assertEquals(128L, computeHeroNumbers(agg, sess)?.sessionCount)
    }

    @Test
    fun sessionCountFallsBackToEventTotalWhenNoSessionsResponse() {
        val agg = AggregatesResponse(totals = Totals(events = 500))
        assertEquals(500L, computeHeroNumbers(agg, null)?.sessionCount)
    }

    @Test
    fun sessionCountIsZeroWhenOnlyAnEmptySessionsResponseHasLoaded() {
        val sess = SessionsResponse(totalSessions = 0)
        assertEquals(0L, computeHeroNumbers(null, sess)?.sessionCount)
    }

    @Test
    fun momentumChangeNullWithFewerThanTwoDailyBuckets() {
        val agg = AggregatesResponse(daily = listOf(DailyPoint(day = "d1", total = 10)))
        assertNull(computeHeroNumbers(agg, null)?.momentumChange)
    }

    @Test
    fun momentumChangeNullWithNoDailyBuckets() {
        val agg = AggregatesResponse(daily = emptyList())
        assertNull(computeHeroNumbers(agg, null)?.momentumChange)
    }

    @Test
    fun momentumChangeNullWhenFirstHalfSumsToZero() {
        val agg = AggregatesResponse(
            daily = listOf(
                DailyPoint(day = "d1", total = 0),
                DailyPoint(day = "d2", total = 0),
                DailyPoint(day = "d3", total = 50),
                DailyPoint(day = "d4", total = 50),
            ),
        )
        assertNull(computeHeroNumbers(agg, null)?.momentumChange)
    }

    @Test
    fun momentumDetectsAnIncrease() {
        // first half (d1,d2) sums 100, second half (d3,d4) sums 150 -> +50% (rendered as
        // "trending up", never as "+50%" — see HeroNumbers.momentumIsIncrease's KDoc).
        val agg = AggregatesResponse(
            daily = listOf(
                DailyPoint(day = "d1", total = 50),
                DailyPoint(day = "d2", total = 50),
                DailyPoint(day = "d3", total = 70),
                DailyPoint(day = "d4", total = 80),
            ),
        )
        val hero = computeHeroNumbers(agg, null)
        assertEquals(50, hero?.momentumChange)
        assertTrue(hero!!.momentumIsIncrease)
    }

    @Test
    fun momentumDetectsADecrease() {
        // first half (d1,d2) sums 150, second half (d3,d4) sums 100 -> -33% (rendered as
        // "trending down", never as "-33%").
        val agg = AggregatesResponse(
            daily = listOf(
                DailyPoint(day = "d1", total = 80),
                DailyPoint(day = "d2", total = 70),
                DailyPoint(day = "d3", total = 50),
                DailyPoint(day = "d4", total = 50),
            ),
        )
        val hero = computeHeroNumbers(agg, null)
        assertEquals(33, hero?.momentumChange)
        assertFalse(hero!!.momentumIsIncrease)
    }

    @Test
    fun standoutEventIsTheTopEventName() {
        val agg = AggregatesResponse(topEvents = listOf(TopEvent(eventName = "checkout", count = 10)))
        assertEquals("checkout", computeHeroNumbers(agg, null)?.standoutEvent)
    }

    @Test
    fun standoutEventIsNullWhenNoTopEvents() {
        val agg = AggregatesResponse()
        assertNull(computeHeroNumbers(agg, null)?.standoutEvent)
    }
}
