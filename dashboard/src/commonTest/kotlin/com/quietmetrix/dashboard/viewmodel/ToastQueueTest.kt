package com.quietmetrix.dashboard.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure state-transition coverage for [DashboardState.toast] / [DashboardViewModel.showToast] /
 * [DashboardViewModel.dismissToast] — the bottom-center auto-dismissing notification described in
 * design_handoff_dashboard_redesign/README.md > Interactions & Behavior > "Toasts" ("bottom-center,
 * auto-dismiss ~5s, optional action button, close (✕) button") and > State Management
 * (`toast: { emoji, title, body, err, actionLabel, action } | null`).
 *
 * No Compose involved: [DashboardViewModel] takes its [kotlinx.coroutines.CoroutineScope] as a
 * constructor parameter (defaulting to its own production scope for real callers), so these tests
 * inject [runTest]'s virtual-time `TestScope` and drive the ~5s auto-dismiss timer with
 * [advanceTimeBy] instead of sleeping for real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToastQueueTest {

    @Test
    fun showToastPublishesItImmediately() = runTest {
        val vm = DashboardViewModel(scope = this)
        val toast = Toast(emoji = "✅", title = "App created")

        vm.showToast(toast)

        assertEquals(toast, vm.state.value.toast)
    }

    @Test
    fun toastAutoDismissesAfterFiveSeconds() = runTest {
        val vm = DashboardViewModel(scope = this)
        vm.showToast(Toast(emoji = "✅", title = "App created"))

        advanceTimeBy(4_999)
        assertTrue(vm.state.value.toast != null, "should still be showing just before 5s")

        advanceTimeBy(2)
        assertNull(vm.state.value.toast, "should have auto-dismissed at/after 5s")
    }

    @Test
    fun aNewToastReplacesAnOldOneAndCancelsItsPendingAutoDismiss() = runTest {
        val vm = DashboardViewModel(scope = this)
        val first = Toast(emoji = "✅", title = "First")
        val second = Toast(emoji = "⚠️", title = "Second", err = true)

        vm.showToast(first)
        advanceTimeBy(3_000) // first's timer is now 3s into its 5s countdown
        vm.showToast(second)

        // If the first toast's timer weren't cancelled, it would fire ~2s from here (at the
        // 5s-since-`first`-was-shown mark) and incorrectly dismiss `second`.
        advanceTimeBy(2_001)
        assertEquals(second, vm.state.value.toast, "first toast's stale timer must not dismiss the second toast")

        // second's own full ~5s timer should still fire on schedule.
        advanceTimeBy(3_000)
        assertNull(vm.state.value.toast)
    }

    @Test
    fun dismissToastClosesImmediatelyAndCancelsThePendingAutoDismiss() = runTest {
        val vm = DashboardViewModel(scope = this)
        vm.showToast(Toast(emoji = "✅", title = "App created"))

        vm.dismissToast()
        assertNull(vm.state.value.toast)

        // The cancelled auto-dismiss job must not crash or otherwise misbehave once its delay
        // would have elapsed.
        advanceTimeBy(5_001)
        assertNull(vm.state.value.toast)
    }

    @Test
    fun dismissToastIsSafeToCallWithNoToastShowing() = runTest {
        val vm = DashboardViewModel(scope = this)

        vm.dismissToast()

        assertNull(vm.state.value.toast)
    }

    @Test
    fun toastCarriesOptionalActionButtonFields() = runTest {
        val vm = DashboardViewModel(scope = this)
        var actionInvoked = false
        val toast = Toast(
            emoji = "🔑",
            title = "Key regenerated",
            body = "The old key stopped working immediately.",
            actionLabel = "Copy key",
            action = { actionInvoked = true },
        )

        vm.showToast(toast)

        val shown = assertNotNull(vm.state.value.toast)
        assertEquals("Copy key", shown.actionLabel)
        assertNotNull(shown.action).invoke()
        assertTrue(actionInvoked)
    }

    @Test
    fun errToastIsFlaggedForErrorStyling() = runTest {
        val vm = DashboardViewModel(scope = this)
        vm.showToast(Toast(emoji = "⚠️", title = "Refresh failed", err = true))

        assertTrue(vm.state.value.toast?.err == true)
    }

    @Test
    fun defaultToastIsNotAnErrorAndHasNoAction() = runTest {
        val vm = DashboardViewModel(scope = this)
        vm.showToast(Toast(emoji = "✅", title = "Done"))

        val shown = assertNotNull(vm.state.value.toast)
        assertFalse(shown.err)
        assertNull(shown.actionLabel)
        assertNull(shown.action)
    }
}
