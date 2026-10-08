package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TopEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverviewHeroLogicTest {
    @Test fun nullWhenNeitherResponseHasLoaded() = assertNull(computeHeroNumbers(null, null))

    @Test fun peopleUsesOnlyTheExactBackendPeopleMeasure() {
        val hero = computeHeroNumbers(AggregatesResponse(people = 120, totals = com.quietmetrix.dashboard.api.Totals(events = 500)), SessionsResponse(totalSessions = 128))
        assertEquals(120L, hero?.peopleCount)
        assertTrue(hero?.hasActivity == true)
    }

    @Test fun sessionsAndEventsNeverBecomePeople() {
        val hero = computeHeroNumbers(AggregatesResponse(totals = com.quietmetrix.dashboard.api.Totals(events = 500)), SessionsResponse(totalSessions = 128))
        assertNull(hero?.peopleCount)
        assertTrue(hero?.hasActivity == true)
    }

    @Test fun noActivityIsDistinctFromUnavailablePeople() {
        val hero = computeHeroNumbers(AggregatesResponse(people = null), SessionsResponse(totalSessions = 0))
        assertFalse(hero?.hasActivity ?: true)
        assertNull(hero?.peopleCount)
    }

    @Test fun comparisonUsesEqualPeriodPeopleAndRetainsDirection() {
        val up = computeHeroNumbers(AggregatesResponse(people = 118), SessionsResponse(), AggregatesResponse(people = 100))
        assertEquals(18, up?.changePercent)
        val down = computeHeroNumbers(AggregatesResponse(people = 82), SessionsResponse(), AggregatesResponse(people = 100))
        assertEquals(-18, down?.changePercent)
    }

    @Test fun inlinePriorPeriodMeasureWorksWithoutDetailedComparisonState() {
        val hero = computeHeroNumbers(AggregatesResponse(people = 128, peopleComparison = 108), SessionsResponse())
        assertEquals(18, hero?.changePercent)
    }

    @Test fun comparisonIsUnavailableWhenPreviousMeasureIsSuppressedOrZero() {
        assertNull(computeHeroNumbers(AggregatesResponse(people = 20), SessionsResponse(), AggregatesResponse(people = null))?.changePercent)
        assertNull(computeHeroNumbers(AggregatesResponse(people = 20), SessionsResponse(), AggregatesResponse(people = 0))?.changePercent)
    }

    @Test fun standoutEventIsFormattedForDisplay() {
        val hero = computeHeroNumbers(AggregatesResponse(topEvents = listOf(TopEvent(eventName = "open_book", count = 10))), null)
        assertEquals("open book", hero?.standoutEvent)
    }
}
