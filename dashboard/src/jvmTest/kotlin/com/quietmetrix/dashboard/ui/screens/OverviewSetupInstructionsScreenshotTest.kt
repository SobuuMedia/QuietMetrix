package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 21's Overview Empty-state setup instructions
 * (`OverviewScreen.kt`'s `SetupInstructionsSection`) — replaces the 3-card row/"Worth a look"/key
 * card region (not the vitals grid or daily-volume chart, which keep their own Task 19/20 all-
 * zero/`EmptyState` treatments) when `OverviewTopListsLogic.kt`'s `isOverviewEmpty` is true, per
 * design_handoff_dashboard_redesign/README.md > Interactions & Behavior > "Empty state": "numbered
 * setup instructions (SDK install snippet, init snippet, 'open your app' step) and a live 'waiting
 * for first event' indicator; a shared Kotlin(KMP)/Swift/JavaScript language switch controls every
 * code snippet".
 *
 * Code snippets are shortened real excerpts from `docs/sdk/android.md`/`docs/sdk/ios.md`/
 * `docs/sdk/web.md`, using those docs' own `qm_ak_your_api_key`/`https://your-server.com/api/v1`
 * placeholders — never a real API key, which per `KeyCard`'s KDoc isn't recoverable from this
 * dashboard's state anyway.
 *
 * Real-crop derivation checked and REJECTED: `03-overview-empty.png`'s setup-instructions region
 * (below the hero's "👋 Welcome to Sobuu" crop already claimed by `OverviewHeroScreenshotTest`) is
 * cut off by the mockup toolbar before any of the 3 numbered steps become legible — same below-
 * the-fold finding as every other Task 19-21 section golden on this screen.
 */
@OptIn(ExperimentalTestApi::class)
class OverviewSetupInstructionsScreenshotTest {

    @Suppress("DEPRECATION")
    @Test
    fun defaultsToKotlinAndMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    SetupInstructionsSection(modifier = Modifier.width(640.dp))
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/setup-instructions/kotlin.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun switchingToSwiftMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    SetupInstructionsSection(modifier = Modifier.width(640.dp))
                }
            }
        }
        waitForIdle()
        onNodeWithText("Swift (iOS)").performClick()
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/setup-instructions/swift.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun switchingToJavaScriptMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    SetupInstructionsSection(modifier = Modifier.width(640.dp))
                }
            }
        }
        waitForIdle()
        onNodeWithText("JavaScript (Web)").performClick()
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/setup-instructions/javascript.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun showsTheWaitingForFirstEventIndicator() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    SetupInstructionsSection(modifier = Modifier.width(640.dp))
                }
            }
        }
        waitForIdle()
        onNodeWithText("⏳ Waiting for your first event…").assertExists()
    }
}
