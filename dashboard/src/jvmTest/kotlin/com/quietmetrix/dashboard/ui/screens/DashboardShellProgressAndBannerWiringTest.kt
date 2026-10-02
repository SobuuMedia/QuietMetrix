package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.toUiError
import com.quietmetrix.dashboard.theme.LocalThemeModeSetter
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import com.quietmetrix.dashboard.viewmodel.DataSection
import com.quietmetrix.dashboard.viewmodel.withSectionError
import kotlin.test.Test

/**
 * End-to-end wiring coverage proving [DashboardShell] actually surfaces Task 17's
 * [HeaderProgressBar]/[ServerUnreachableBanner] from real [DashboardState] fields — not just
 * that the standalone composables work in isolation (see
 * `HeaderProgressBarAndBannerScreenshotTest`/`HeaderProgressBarAndBannerInteractionTest` for
 * that). Mirrors `DashboardShellNavTest`'s `renderShellAt` helper.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardShellProgressAndBannerWiringTest {

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's reviewed
    // convention, commit ab300e0): v1 runComposeUiTest is what the existing
    // screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    private fun renderShellAt(
        state: DashboardState,
        widthDp: Int = 1024,
        heightDp: Int = 900,
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) =
        runComposeUiTest {
            val viewModel = DashboardViewModel()
            setContent {
                CompositionLocalProvider(LocalThemeModeSetter provides {}) {
                    QuietMetrixTheme(ThemeMode.Dark) {
                        Box(modifier = Modifier.size(widthDp.dp, heightDp.dp)) {
                            DashboardShell(
                                viewModel = viewModel,
                                state = state,
                                snackbarState = SnackbarHostState(),
                            ) { _ -> Text("content") }
                        }
                    }
                }
            }
            waitForIdle()
            block()
        }

    @Test
    fun progressBarIsAbsentOnAQuietNonLoadingNonRefreshingScreen() =
        renderShellAt(DashboardState()) {
            onAllNodesWithContentDescription("Loading").assertCountEquals(0)
        }

    @Test
    fun progressBarAppearsWhileLoading() =
        renderShellAt(DashboardState(loading = true)) {
            onNodeWithContentDescription("Loading").assertExists()
        }

    @Test
    fun progressBarAppearsWhileRefreshing() =
        renderShellAt(DashboardState(refreshing = true)) {
            onNodeWithContentDescription("Loading").assertExists()
        }

    @Test
    fun serverUnreachableBannerAppearsWhenASectionHasANetworkError() {
        val state = DashboardState().withSectionError(
            DataSection.Aggregates,
            RuntimeException("Connection refused").toUiError("GET /projects/{id}/aggregates"),
        )
        renderShellAt(state) {
            onNodeWithText("We can't reach your QuietMetrix server.", substring = true).assertExists()
        }
    }

    @Test
    fun serverUnreachableBannerIsAbsentWhenNoSectionHasANetworkError() =
        renderShellAt(DashboardState()) {
            onAllNodesWithText("We can't reach your QuietMetrix server.", substring = true).assertCountEquals(0)
        }

    /**
     * Regression test for the two-error-mechanism collision a code review caught: a background
     * loader's connection failure (recorded in [DashboardState.sectionErrors], surfacing
     * [ServerUnreachableBanner]) and a user-initiated action's connection failure (recorded in
     * [DashboardState.error], which [ErrorBanner] would otherwise render) can both be set at the
     * same time — both are [com.quietmetrix.dashboard.api.ErrorKind.Network] failures, but from
     * independent code paths, so nothing previously stopped them from being true simultaneously.
     * Only one banner must render: see [ServerUnreachableBanner]'s KDoc for why this banner wins.
     */
    @Test
    fun onlyTheServerUnreachableBannerRendersWhenBothErrorMechanismsAreSetAtOnce() {
        val state = DashboardState(
            error = RuntimeException("Connection refused").toUiError("POST /projects"),
        ).withSectionError(
            DataSection.Aggregates,
            RuntimeException("Connection refused").toUiError("GET /projects/{id}/aggregates"),
        )
        renderShellAt(state) {
            // The connectivity banner is present...
            onNodeWithText("We can't reach your QuietMetrix server.", substring = true).assertExists()
            // ...and ErrorBanner is not — its dismiss (✕) button is the one node unique to it
            // (ServerUnreachableBanner has no dismiss button at all), so its absence proves
            // ErrorBanner itself did not render, not just that its text happens to differ.
            onAllNodesWithContentDescription("Dismiss").assertCountEquals(0)
        }
    }
}
