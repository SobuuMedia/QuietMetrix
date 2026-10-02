package com.quietmetrix.dashboard.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for Task 21's Overview "Worth a look" dismiss/snooze wiring
 * ([DashboardState.dismissedWorthALookIds] / [DashboardViewModel.hideWorthALook]) — both actions
 * hide the card immediately and surface an undo toast through the SAME queue [ToastQueueTest]
 * already covers ([DashboardViewModel.showToast]/[DashboardViewModel.dismissToast]), per
 * design_handoff_dashboard_redesign/README.md > Interactions & Behavior > "Dismiss/snooze": "both
 * remove a 'Worth a look' card immediately and show an undo toast for ~5s."
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorthALookQueueTest {

    @Test
    fun dismissHidesTheCardImmediately() = runTest {
        val vm = DashboardViewModel(scope = this)

        vm.hideWorthALook(id = "offline_sync", snooze = false)

        assertTrue("offline_sync" in vm.state.value.dismissedWorthALookIds)
    }

    @Test
    fun dismissShowsAnUndoToastThroughTheRealToastQueue() = runTest {
        val vm = DashboardViewModel(scope = this)

        vm.hideWorthALook(id = "offline_sync", snooze = false)

        val toast = assertNotNull(vm.state.value.toast, "dismiss must show a toast via showToast")
        assertEquals("👋", toast.emoji)
        assertFalse(toast.err)
        assertEquals("Undo", toast.actionLabel)
        assertNotNull(toast.action)
    }

    @Test
    fun snoozeHidesTheCardAndShowsADistinctSnoozeToast() = runTest {
        val vm = DashboardViewModel(scope = this)

        vm.hideWorthALook(id = "top_screen", snooze = true)

        assertTrue("top_screen" in vm.state.value.dismissedWorthALookIds)
        val toast = assertNotNull(vm.state.value.toast)
        assertEquals("💤", toast.emoji)
        assertEquals("Undo", toast.actionLabel)
    }

    @Test
    fun undoActionRestoresTheCardAndClearsTheToast() = runTest {
        val vm = DashboardViewModel(scope = this)
        vm.hideWorthALook(id = "offline_sync", snooze = false)
        val undo = assertNotNull(vm.state.value.toast?.action)

        undo.invoke()

        assertFalse("offline_sync" in vm.state.value.dismissedWorthALookIds)
        assertNull(vm.state.value.toast)
    }

    @Test
    fun hidingTwoDifferentCardsKeepsBothHidden() = runTest {
        val vm = DashboardViewModel(scope = this)

        vm.hideWorthALook(id = "offline_sync", snooze = false)
        vm.hideWorthALook(id = "top_screen", snooze = true)

        assertEquals(setOf("offline_sync", "top_screen"), vm.state.value.dismissedWorthALookIds)
    }
}
