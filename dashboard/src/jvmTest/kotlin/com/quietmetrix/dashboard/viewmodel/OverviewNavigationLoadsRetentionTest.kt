package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.nav.NavDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for Task 19's fix round 1: `DashboardViewModel.loadActive`'s `Overview` branch
 * must fetch retention (not just aggregates+sessions), so the Overview vitals grid's "Came back
 * next day" card (`OverviewScreen.kt`'s `VitalsSection`) has real data without requiring a
 * separate visit to the Retention tab first.
 *
 * This codebase has no HTTP-mocking seam for [com.quietmetrix.dashboard.api.ApiClient] (it
 * constructs its own real `HttpClient` internally, with no injectable engine) and `loadActive`/
 * `loadRetentionOnce` are both `private`, so this can't be a pure unit test of the dispatch table
 * in isolation. Instead this test points [DashboardViewModel] at an address nothing listens on
 * (`127.0.0.1:1` — port 1 is a reserved, sub-1024 port no unprivileged test process can ever be
 * bound to) and asserts that a [DataSection.Retention] entry appears in
 * [DashboardState.sectionErrors] after navigating to [NavDestination.Overview] — a real section
 * error only appears if the retention fetch was actually attempted and failed, so this proves the
 * dispatch wiring without needing a working server. Connecting to a closed loopback port fails
 * near-instantly (`ECONNREFUSED`, no real network round trip), so this is deterministic, not a
 * flaky network test.
 *
 * Uses a real `runBlocking` + a short real-time poll (not `kotlinx.coroutines.test.runTest`'s
 * virtual-time scheduler) because [DashboardViewModel.navigateTo] is fire-and-forget
 * (`scope.launch { ... }`, no `Job` returned) and the underlying HTTP call is genuine async I/O
 * on a real dispatcher, not a virtual-time `delay` — a virtual-time test runner has no way to
 * "wait" for it.
 */
class OverviewNavigationLoadsRetentionTest {

    @Test
    fun navigatingToOverviewAlsoFetchesRetention() = runBlocking {
        val vm = DashboardViewModel()
        vm.init("http://127.0.0.1:1")
        vm.selectProject("task19-regression-project")

        vm.navigateTo(NavDestination.Overview)

        withTimeout(5_000) {
            while (DataSection.Retention !in vm.state.value.sectionErrors) {
                delay(20)
            }
        }

        assertTrue(
            DataSection.Retention in vm.state.value.sectionErrors,
            "Navigating to Overview must attempt a retention fetch (recorded as a " +
                "DataSection.Retention section error here, since nothing is listening at the " +
                "test's fake server address) — if this never appears, loadActive's Overview " +
                "branch stopped calling loadRetentionOnce().",
        )
    }
}
