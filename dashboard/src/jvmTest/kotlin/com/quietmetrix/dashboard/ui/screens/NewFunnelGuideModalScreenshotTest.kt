package com.quietmetrix.dashboard.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 28's re-scoped "＋ New funnel" instructional panel
 * (`FunnelsScreen.kt`'s `NewFunnelGuideModal`), opened now that the button is re-enabled (see
 * `NewFunnelButton`'s KDoc for the re-scoping decision this task implements). Genuinely reuses
 * Task 21's `SetupInstructionsSection` pattern (`OverviewScreen.kt`): the same `SetupLanguage`
 * Kotlin/Swift/JavaScript switch and `SetupCodeBlock` composable (both widened from `private` to
 * `internal` by this task so this file can call them directly, rather than duplicating either) —
 * just hosted in a `ModalShell` (Task 14) instead of an inline empty-state card.
 *
 * Code excerpts are shortened real excerpts from `docs/sdk/android.md`/`docs/sdk/ios.md`/
 * `docs/sdk/web.md`'s own "Funnels" sections (the `Funnel`/`FunnelStep` declaration + registering
 * it on `QuietMetrixConfig`/`init`), the same "real excerpt, not fabricated" standard
 * `OverviewSetupInstructionsScreenshotTest`'s own KDoc documents for Task 21's snippets.
 *
 * No `_source/` crop shows this panel at all — it's a brand-new modal with no equivalent region in
 * `07-funnels-normal.png` (whose only visible content is the H1 + Ask drawer, per
 * `FunnelsScreenshotTest`'s own hero-region derivation note) — so this is derived directly from
 * `NewFunnelGuideModal`'s real rendered output, same "no real-crop exists for a net-new element"
 * category as `funnels/cards-normal.png`.
 */
@OptIn(ExperimentalTestApi::class)
class NewFunnelGuideModalScreenshotTest {

    @Suppress("DEPRECATION")
    @Test
    fun defaultsToKotlinAndMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                NewFunnelGuideModal(onDismiss = {})
            }
        }
        waitForIdle()
        onNode(isDialog()).captureToImage().assertMatchesGolden("funnels/new-guide-kotlin.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun switchingToSwiftShowsTheSwiftSnippet() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                NewFunnelGuideModal(onDismiss = {})
            }
        }
        waitForIdle()
        onNodeWithText("Swift (iOS)").performClick()
        waitForIdle()
        onNodeWithText("QuietMetrix.shared.initialize(config: config)", substring = true).assertExists()
    }

    @Suppress("DEPRECATION")
    @Test
    fun switchingToJavaScriptShowsTheJsSnippet() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                NewFunnelGuideModal(onDismiss = {})
            }
        }
        waitForIdle()
        onNodeWithText("JavaScript (Web)").performClick()
        waitForIdle()
        onNodeWithText("init({", substring = true).assertExists()
    }

    @Suppress("DEPRECATION")
    @Test
    fun doneButtonDismissesTheModal() = runComposeUiTest {
        var dismissed = false
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                NewFunnelGuideModal(onDismiss = { dismissed = true })
            }
        }
        waitForIdle()
        onNodeWithText("Done").performClick()
        waitForIdle()
        kotlin.test.assertTrue(dismissed)
    }
}
