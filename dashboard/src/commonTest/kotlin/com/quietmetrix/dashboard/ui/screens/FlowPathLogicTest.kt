package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.Transition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-logic coverage for Task 22's Flow breadcrumb ([FlowScreen.kt]'s extraction target):
 * ranking "Start from" screens, building a screen's next-step distribution, extending the
 * breadcrumb path up to [FLOW_PATH_MAX_DEPTH] deep, and the honest no-op platform filter — see
 * [filterTransitionsByPlatform]'s KDoc for the data-honesty finding behind that last one.
 */
class FlowPathLogicTest {

    private val sample = listOf(
        Transition(fromScreen = "home", toScreen = "search", count = 60),
        Transition(fromScreen = "home", toScreen = "settings", count = 40),
        Transition(fromScreen = "search", toScreen = "results", count = 30),
        Transition(fromScreen = "search", toScreen = null, count = 10), // 👋 left the app
    )

    // -- topStartScreens -----------------------------------------------------------

    @Test
    fun topStartScreensRanksByTotalOutgoingVolumeDescending() {
        // home: 60+40=100 total out; search: 30+10=40 total out.
        assertEquals(listOf("home", "search"), topStartScreens(sample))
    }

    @Test
    fun topStartScreensRespectsLimit() {
        assertEquals(listOf("home"), topStartScreens(sample, limit = 1))
    }

    @Test
    fun topStartScreensEmptyWhenNoTransitions() {
        assertEquals(emptyList(), topStartScreens(emptyList()))
    }

    // -- nextStepOptions -------------------------------------------------------------

    @Test
    fun nextStepOptionsSortsByCountDescendingAndComputesPercentOfThatScreensOwnOutgoingTotal() {
        val options = nextStepOptions(sample, "home")
        assertEquals(listOf("search", "settings"), options.map { it.screen })
        assertEquals(60.0, options[0].percent)
        assertEquals(40.0, options[1].percent)
    }

    @Test
    fun nextStepOptionsIncludesAnExitOptionWhenANullToScreenTransitionExists() {
        val options = nextStepOptions(sample, "search")
        assertEquals(2, options.size)
        assertEquals("results", options[0].screen)
        assertNull(options[1].screen)
        assertTrue(options[1].isExit)
        assertFalse(options[0].isExit)
        // 30 of 40 total outgoing from "search" -> 75%; 10 of 40 -> 25%.
        assertEquals(75.0, options[0].percent)
        assertEquals(25.0, options[1].percent)
    }

    @Test
    fun nextStepOptionsEmptyForAScreenWithNoOutgoingTransitions() {
        assertEquals(emptyList(), nextStepOptions(sample, "settings"))
    }

    // -- canExtendFlowPath / extendFlowPath -------------------------------------------

    @Test
    fun canExtendFlowPathTrueBelowMaxDepthFalseAtMaxDepth() {
        assertTrue(canExtendFlowPath(listOf("home")))
        assertTrue(canExtendFlowPath(List(FLOW_PATH_MAX_DEPTH - 1) { "s$it" }))
        assertFalse(canExtendFlowPath(List(FLOW_PATH_MAX_DEPTH) { "s$it" }))
    }

    @Test
    fun extendFlowPathAppendsTheChosenNonExitScreen() {
        val option = FlowStepOption(screen = "search", count = 60, percent = 60.0)
        assertEquals(listOf("home", "search"), extendFlowPath(listOf("home"), option))
    }

    @Test
    fun extendFlowPathReturnsNullForTheExitOptionNotClickable() {
        val exit = FlowStepOption(screen = null, count = 10, percent = 25.0)
        assertNull(extendFlowPath(listOf("search"), exit))
    }

    @Test
    fun extendFlowPathReturnsNullOnceAlreadyAtMaxDepth() {
        val maxedPath = List(FLOW_PATH_MAX_DEPTH) { "s$it" }
        val option = FlowStepOption(screen = "one-more", count = 5, percent = 10.0)
        assertNull(extendFlowPath(maxedPath, option))
    }

    // -- buildFlowBreadcrumb ------------------------------------------------------------

    @Test
    fun buildFlowBreadcrumbReturnsOneColumnPerPathStepWithThatStepsOwnOptions() {
        val columns = buildFlowBreadcrumb(listOf("home", "search"), sample)
        assertEquals(listOf("home", "search"), columns.map { it.screen })
        assertEquals(listOf("search", "settings"), columns[0].options.map { it.screen })
        assertEquals(listOf("results", null), columns[1].options.map { it.screen })
    }

    @Test
    fun buildFlowBreadcrumbEmptyPathIsEmpty() {
        assertEquals(emptyList(), buildFlowBreadcrumb(emptyList(), sample))
    }

    // -- filterTransitionsByPlatform ------------------------------------------------------

    @Test
    fun filterTransitionsByPlatformIsAnHonestNoOpForEveryPlatformSinceTheBackendTracksNoPlatformDimension() {
        // See the function's KDoc: `Transition`/server `TransitionItem` carry no platform field,
        // so there is no real per-platform split to compute — this asserts the function returns
        // the input unchanged for every platform rather than fabricating one.
        FlowPlatform.entries.forEach { platform ->
            assertEquals(sample, filterTransitionsByPlatform(sample, platform))
        }
    }

    // -- computePlatformStatTiles (Task 23: Flow 3-card row) --------------------------------

    @Test
    fun computePlatformStatTilesRanksByCountDescending() {
        val tiles = computePlatformStatTiles(listOf(BreakdownItem("ios", 38), BreakdownItem("android", 54), BreakdownItem("web", 8)))
        assertEquals(listOf("android", "ios", "web"), tiles.map { it.name })
    }

    @Test
    fun computePlatformStatTilesCapsAtThreeTilesButNeverPadsBelowThat() {
        val fourPlatforms = listOf(
            BreakdownItem("android", 100), BreakdownItem("ios", 80), BreakdownItem("web", 40), BreakdownItem("other", 10),
        )
        assertEquals(listOf("android", "ios", "web"), computePlatformStatTiles(fourPlatforms).map { it.name })

        val onePlatform = listOf(BreakdownItem("android", 100))
        assertEquals(listOf("android"), computePlatformStatTiles(onePlatform).map { it.name })
    }

    @Test
    fun computePlatformStatTilesEmptyInputIsEmpty() {
        assertEquals(emptyList(), computePlatformStatTiles(emptyList()))
    }

    @Test
    fun computePlatformStatTilesFiltersZeroCountPlatforms() {
        val tiles = computePlatformStatTiles(listOf(BreakdownItem("android", 54), BreakdownItem("web", 0)))
        assertEquals(listOf("android"), tiles.map { it.name })
    }

    // -- deviceClassEmoji (Task 23: Flow 3-card row) -----------------------------------------

    @Test
    fun deviceClassEmojiMapsKnownClassesCaseInsensitively() {
        assertEquals("📱", deviceClassEmoji("phone"))
        assertEquals("📱", deviceClassEmoji("Phone"))
        assertEquals("📟", deviceClassEmoji("tablet"))
        assertEquals("🖥️", deviceClassEmoji("DESKTOP"))
    }

    @Test
    fun deviceClassEmojiFallsBackToAGenericGlyphForAnUnrecognizedClass() {
        assertEquals("📦", deviceClassEmoji("smart-tv"))
        assertEquals("📦", deviceClassEmoji("(unknown)"))
    }
}
