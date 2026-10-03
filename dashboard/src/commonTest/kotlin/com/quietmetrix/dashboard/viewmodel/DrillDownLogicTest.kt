package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.TopScreen
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
        assertTrue(result.detail.contains("No matching"))
    }
}
