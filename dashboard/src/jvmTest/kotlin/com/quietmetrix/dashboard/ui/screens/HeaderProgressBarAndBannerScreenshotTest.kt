package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.api.toUiError
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 17's header progress bar and server-unreachable banner
 * (`DashboardShell.kt`'s `HeaderProgressBarTrack`/`ServerUnreachableBanner`), derived per
 * `goldens/README.md`'s "How a task derives its own golden" process:
 *
 * - [progressBarTrackMatchesGolden]: real-crop-derived — `_source/02-overview-loading.png` shows
 *   the 3px sliding lime bar, unobscured by the Ask-drawer overlay, at a snapshot fraction of
 *   ~0.34 of the frame's width; [HeaderProgressBarTrack] is rendered at that same fixed fraction
 *   (the live animation in [HeaderProgressBar] is pulled out specifically so this is possible)
 *   rather than the exact JPEG-compressed fill color, per the token-over-pixel judgment call
 *   documented on that composable.
 * - [serverUnreachableBannerMatchesGolden]: real-crop-derived — `_source/04-overview-errors.png`
 *   shows the coral banner's left ~448px fully unobscured (the Ask-drawer overlay begins further
 *   right than in the other `_source/` frames for this particular band), including the 🔌 icon
 *   and "We can't reach your QuietMetrix server" copy; the "Try again"/"Copy error details"
 *   buttons sit past that edge in the source frame, so their presence/placement is spec-derived
 *   (this task's brief + README's Header bullet) rather than crop-derived.
 */
@OptIn(ExperimentalTestApi::class)
class HeaderProgressBarAndBannerScreenshotTest {

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun progressBarTrackMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    HeaderProgressBarTrack(fraction = 0.336f)
                }
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("header/progress-bar.png")
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun serverUnreachableBannerMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ServerUnreachableBanner(
                        error = RuntimeException("Connection refused").toUiError("GET /projects/{id}/aggregates"),
                        onRetry = {},
                    )
                }
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("header/server-unreachable-banner.png")
    }
}
