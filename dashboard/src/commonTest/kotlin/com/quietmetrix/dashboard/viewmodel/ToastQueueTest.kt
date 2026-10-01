package com.quietmetrix.dashboard.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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

    // ---- clearingToastIfStillCurrent: the Job-identity guard behind showToast's auto-dismiss ----
    //
    // Job.cancel() is cooperative — it only takes effect at a suspension point — so if a stale
    // auto-dismiss timer's delay() had *already* resumed (e.g. its body is mid-execution on
    // another thread under the real Dispatchers.Default-backed production scope) at the exact
    // moment it's superseded, cancel() alone cannot stop it from running its clear. A genuine
    // concurrent-thread test for that race would depend on real timing (hitting a multi-millisecond
    // window at the exact moment a coroutine resumes) and would be inherently flaky; these tests
    // instead cover the identity-check decision itself — the mechanism that makes the stale body's
    // clear a no-op even if it does run — deterministically and without any coroutines or timing.

    @Test
    fun clearingToastIfStillCurrentNoOpsWhenTheSchedulingJobIsNoLongerCurrent() {
        val toast = Toast(emoji = "⚠️", title = "Second")
        val state = DashboardState(toast = toast)
        val staleJob = Job() // the superseded toast's own auto-dismiss timer
        val currentJob = Job() // a different toast's timer, now the one actually in flight

        val result = state.clearingToastIfStillCurrent(scheduledJob = staleJob, currentJob = currentJob)

        assertSame(toast, result.toast, "a stale timer must not clear a toast it wasn't scheduled for")
    }

    @Test
    fun clearingToastIfStillCurrentClearsWhenTheSchedulingJobIsStillCurrent() {
        val job = Job()
        val state = DashboardState(toast = Toast(emoji = "✅", title = "Done"))

        val result = state.clearingToastIfStillCurrent(scheduledJob = job, currentJob = job)

        assertNull(result.toast, "the toast's own still-current timer must clear it as normal")
    }

    @Test
    fun clearingToastIfStillCurrentNoOpsWhenCurrentJobIsNull() {
        val toast = Toast(emoji = "✅", title = "Done")
        val state = DashboardState(toast = toast)
        val staleJob = Job() // dismissToast() already cancelled and nulled this out

        val result = state.clearingToastIfStillCurrent(scheduledJob = staleJob, currentJob = null)

        assertSame(toast, result.toast, "a timer superseded by a manual dismiss must not clear whatever shows next")
    }
}
