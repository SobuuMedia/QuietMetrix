package com.quietmetrix.dashboard.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.api.toUiError
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Functional-wiring coverage for Task 17's `HeaderProgressBar`/`ServerUnreachableBanner`
 * (`DashboardShell.kt`), independent of the (golden-tested) visual appearance covered by
 * `HeaderProgressBarAndBannerScreenshotTest`.
 */
@OptIn(ExperimentalTestApi::class)
class HeaderProgressBarAndBannerInteractionTest {

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun progressBarRendersNothingWhenNotVisible() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                HeaderProgressBar(visible = false)
            }
        }
        waitForIdle()

        // No "Loading" content-description node exists at all — not just hidden — confirming
        // the quiet-screen case reserves zero layout space (see HeaderProgressBar's KDoc).
        onAllNodesWithContentDescription("Loading").assertCountEquals(0)
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun progressBarRendersWhenVisible() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                HeaderProgressBar(visible = true)
            }
        }
        waitForIdle()

        onNodeWithContentDescription("Loading").assertExists()
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun tryAgainButtonFiresOnRetry() = runComposeUiTest {
        var retried = false
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                ServerUnreachableBanner(
                    error = RuntimeException("Connection refused").toUiError("GET /projects/{id}/aggregates"),
                    onRetry = { retried = true },
                )
            }
        }
        waitForIdle()

        onNodeWithText("Try again").performClick()
        waitForIdle()

        assertEquals(true, retried)
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun copyErrorDetailsButtonShowsCopiedConfirmation() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                ServerUnreachableBanner(
                    error = RuntimeException("Connection refused").toUiError("GET /projects/{id}/aggregates"),
                    onRetry = {},
                )
            }
        }
        waitForIdle()

        onNodeWithContentDescription("Copy error details").performClick()
        waitForIdle()

        // CopyErrorButton (ui/components/ErrorDisplay.kt) swaps in this confirmation label for
        // ~1.5s after a successful clipboard write — its presence is the signal the write
        // actually happened (not just that the click didn't crash).
        onNodeWithText("Error details copied to clipboard").assertExists()
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun bannerShowsTheFixedServerUnreachableMessageRegardlessOfErrorKind() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                ServerUnreachableBanner(
                    error = RuntimeException("timeout").toUiError("GET /projects/{id}/sessions"),
                    onRetry = {},
                )
            }
        }
        waitForIdle()

        onNodeWithText("We can't reach your QuietMetrix server.", substring = true).assertExists()
    }
}
