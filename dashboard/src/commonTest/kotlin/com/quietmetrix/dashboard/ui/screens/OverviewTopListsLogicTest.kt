package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.ScreenDuration
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TopScreen
import com.quietmetrix.dashboard.api.Totals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-logic coverage for Task 21's Overview 3-card row ([computeScreenUsage]/
 * [computeBreakdownRows]), "Worth a look" ([computeWorthALookObservations]), and the
 * whole-screen empty signal ([isOverviewEmpty]) — see `OverviewScreen.kt`'s `WorthALookSection`
 * KDoc for the full data-honesty investigation behind [computeWorthALookObservations]'s design.
 */
class OverviewTopListsLogicTest {

    // -- computeScreenUsage ------------------------------------------------------

    @Test
    fun computeScreenUsageJoinsTopScreensWithDurationsByName() {
        val agg = AggregatesResponse(
            topScreens = listOf(TopScreen(screen = "home", count = 100), TopScreen(screen = "search", count = 40)),
            screenDurations = listOf(ScreenDuration(screen = "home", avgMs = 5_000), ScreenDuration(screen = "search", avgMs = 2_000)),
        )
        val rows = computeScreenUsage(agg)
        assertEquals(listOf("home", "search"), rows.map { it.screen })
        assertEquals(5_000L, rows[0].avgDwellMs)
        assertEquals(2_000L, rows[1].avgDwellMs)
    }

    @Test
    fun computeScreenUsageBarFractionIsRelativeToTheTopRow() {
        val agg = AggregatesResponse(
            topScreens = listOf(TopScreen(screen = "home", count = 100), TopScreen(screen = "search", count = 25)),
        )
        val rows = computeScreenUsage(agg)
        assertEquals(1.0, rows[0].barFraction)
        assertEquals(0.25, rows[1].barFraction)
    }

    @Test
    fun computeScreenUsageDefaultsMissingDurationToZero() {
        val agg = AggregatesResponse(topScreens = listOf(TopScreen(screen = "home", count = 10)))
        val rows = computeScreenUsage(agg)
        assertEquals(0L, rows.single().avgDwellMs)
    }

    @Test
    fun computeScreenUsageDropsBlankOrNullScreenNames() {
        val agg = AggregatesResponse(
            topScreens = listOf(TopScreen(screen = null, count = 10), TopScreen(screen = "  ", count = 5), TopScreen(screen = "home", count = 20)),
        )
        val rows = computeScreenUsage(agg)
        assertEquals(listOf("home"), rows.map { it.screen })
    }

    @Test
    fun computeScreenUsageNullOrEmptyAggIsEmpty() {
        assertEquals(emptyList(), computeScreenUsage(null))
        assertEquals(emptyList(), computeScreenUsage(AggregatesResponse()))
    }

    // -- computeBreakdownRows -----------------------------------------------------

    @Test
    fun computeBreakdownRowsSortsDescendingByCount() {
        val rows = computeBreakdownRows(listOf(BreakdownItem("ios", 38), BreakdownItem("android", 54), BreakdownItem("web", 8)))
        assertEquals(listOf("android", "ios", "web"), rows.map { it.name })
    }

    @Test
    fun computeBreakdownRowsFiltersZeroCounts() {
        val rows = computeBreakdownRows(listOf(BreakdownItem("android", 54), BreakdownItem("web", 0)))
        assertEquals(listOf("android"), rows.map { it.name })
    }

    @Test
    fun computeBreakdownRowsBarFractionIsRelativeToMaxAndShareIsOfTotal() {
        val rows = computeBreakdownRows(listOf(BreakdownItem("android", 60), BreakdownItem("ios", 40)))
        assertEquals(1.0, rows[0].barFraction)
        assertEquals(40.0 / 60.0, rows[1].barFraction)
        assertEquals(0.6, rows[0].shareOfTotal)
        assertEquals(0.4, rows[1].shareOfTotal)
    }

    @Test
    fun computeBreakdownRowsUsesUnknownForNullName() {
        val rows = computeBreakdownRows(listOf(BreakdownItem(name = null, count = 5)))
        assertEquals("(unknown)", rows.single().name)
    }

    @Test
    fun computeBreakdownRowsEmptyInputIsEmpty() {
        assertEquals(emptyList(), computeBreakdownRows(emptyList()))
    }

    // -- computeWorthALookObservations --------------------------------------------

    @Test
    fun noObservationsWhenNothingRealStandsOut() {
        val agg = AggregatesResponse(
            topScreens = emptyList(),
            platforms = emptyList(),
            totals = Totals(offline = 0),
        )
        assertEquals(emptyList(), computeWorthALookObservations(agg))
    }

    @Test
    fun nullAggYieldsNoObservations() {
        assertEquals(emptyList(), computeWorthALookObservations(null))
    }

    @Test
    fun offlineObservationOnlyWhenOfflineCountIsPositive() {
        val agg = AggregatesResponse(totals = Totals(offline = 612))
        val obs = computeWorthALookObservations(agg).filterIsInstance<OfflineSyncObservation>()
        assertEquals(612L, obs.single().count)
    }

    @Test
    fun noOfflineObservationWhenOfflineCountIsZero() {
        val agg = AggregatesResponse(totals = Totals(offline = 0))
        assertTrue(computeWorthALookObservations(agg).filterIsInstance<OfflineSyncObservation>().isEmpty())
    }

    @Test
    fun topScreenObservationReportsTheRealMostVisitedScreen() {
        val agg = AggregatesResponse(topScreens = listOf(TopScreen(screen = "home", count = 10), TopScreen(screen = "search", count = 99)))
        val obs = computeWorthALookObservations(agg).filterIsInstance<TopScreenObservation>().single()
        assertEquals("search", obs.screen)
        assertEquals(99L, obs.visits)
    }

    @Test
    fun dominantPlatformObservationOnlyAtOrAboveFiftyPercentShare() {
        val dominant = AggregatesResponse(platforms = listOf(BreakdownItem("android", 60), BreakdownItem("ios", 40)))
        assertEquals(1, computeWorthALookObservations(dominant).filterIsInstance<DominantPlatformObservation>().size)

        val split = AggregatesResponse(platforms = listOf(BreakdownItem("android", 49), BreakdownItem("ios", 51)))
        val splitDominant = computeWorthALookObservations(split).filterIsInstance<DominantPlatformObservation>().single()
        assertEquals("ios", splitDominant.platform)

        val even = AggregatesResponse(platforms = listOf(BreakdownItem("android", 50), BreakdownItem("ios", 50)))
        // Tied at exactly 50/50: the first-sorted row (stable, descending-by-count sort keeps
        // insertion order on ties) is reported, since >= 50% is still honestly a majority/tie,
        // not a fabricated "dominant" claim beyond what the number says.
        assertEquals(1, computeWorthALookObservations(even).filterIsInstance<DominantPlatformObservation>().size)
    }

    @Test
    fun noDominantPlatformObservationBelowFiftyPercentShare() {
        val agg = AggregatesResponse(platforms = listOf(BreakdownItem("android", 40), BreakdownItem("ios", 35), BreakdownItem("web", 25)))
        assertTrue(computeWorthALookObservations(agg).filterIsInstance<DominantPlatformObservation>().isEmpty())
    }

    @Test
    fun observationsAreCappedAtThreeAndOrderedOfflineThenScreenThenPlatform() {
        val agg = AggregatesResponse(
            totals = Totals(offline = 10),
            topScreens = listOf(TopScreen(screen = "home", count = 5)),
            platforms = listOf(BreakdownItem("android", 90), BreakdownItem("ios", 10)),
        )
        val obs = computeWorthALookObservations(agg)
        assertEquals(3, obs.size)
        assertTrue(obs[0] is OfflineSyncObservation)
        assertTrue(obs[1] is TopScreenObservation)
        assertTrue(obs[2] is DominantPlatformObservation)
    }

    // -- isOverviewEmpty -----------------------------------------------------------

    @Test
    fun isOverviewEmptyTrueWhenNoSessionsAndNoEvents() {
        assertTrue(isOverviewEmpty(AggregatesResponse(), SessionsResponse(totalSessions = 0)))
        assertTrue(isOverviewEmpty(null, null))
    }

    @Test
    fun isOverviewEmptyFalseWhenEitherHasRealData() {
        assertEquals(false, isOverviewEmpty(AggregatesResponse(totals = Totals(events = 1)), null))
        assertEquals(false, isOverviewEmpty(null, SessionsResponse(totalSessions = 1)))
    }
}
