package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.EventRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Detail-panel selection state for the Events screen. */
class EventSelectionTest {

    @Test
    fun selectEventOpensPanelAndClearClosesIt() {
        val vm = DashboardViewModel()
        assertNull(vm.state.value.selectedEvent)

        val row = EventRow(eventName = "page_view", screen = "Home")
        vm.selectEvent(row)
        assertEquals("page_view", vm.state.value.selectedEvent?.eventName)

        vm.clearSelectedEvent()
        assertNull(vm.state.value.selectedEvent)
    }
}
