package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.FunnelStepResultDto
import com.quietmetrix.dashboard.api.FunnelSummaryDto
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Component-level screenshot regression coverage for the Funnels hero, switcher, statistic cards,
 * and step list. The 10.30.57 and 10.31.03 handoff screenshots are full-resolution design-tool
 * captures; these generated fixtures are not pixel-derived reference crops and must not be used
 * alone as proof of full-screen parity. The hero test includes the role-gated Create funnel CTA.
 */
@OptIn(ExperimentalTestApi::class)
class FunnelsScreenshotTest {

    private val sizeClass = WindowSizeClass.Expanded

    private fun funnel(key: String, name: String) = FunnelDto(funnelKey = key, name = name)

    private fun results(entered: Int, converted: Int, overallConversion: Double) = FunnelResultsResponse(
        funnel = FunnelSummaryDto(funnelKey = "shelves", name = "Shelves"),
        entered = entered,
        converted = converted,
        overallConversion = overallConversion,
    )

    // ---- Hero (H1) ----

    @Suppress("DEPRECATION")
    @Test
    fun createActionLivesBesideHeroAndIsHiddenForReadOnlyUsers() = runComposeUiTest {
        var canWrite by mutableStateOf(true)
        var creates = 0
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FunnelHero(
                        funnel = funnel("shelves", "Shelves"),
                        results = results(entered = 200, converted = 80, overallConversion = 0.4),
                        onNewFunnel = { creates++ },
                        canWrite = canWrite,
                    )
                }
            }
        }
        onNodeWithText("New funnel").performClick()
        assertEquals(1, creates)
        canWrite = false
        waitForIdle()
        onAllNodesWithText("New funnel").assertCountEquals(0)
    }

    @Suppress("DEPRECATION")
    @Test
    fun heroNormalMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FunnelHero(
                        funnel = funnel("shelves", "Shelves"),
                        results = results(entered = 200, converted = 80, overallConversion = 0.4),
                        onNewFunnel = {},
                        canWrite = true,
                        modifier = Modifier.width(465.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("funnels/hero-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun heroNoEntrantsMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FunnelHero(
                        funnel = funnel("shelves", "Shelves"),
                        results = results(entered = 0, converted = 0, overallConversion = 0.0),
                        modifier = Modifier.width(465.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("funnels/hero-no-entrants.png")
    }

    // ---- Switcher pill row ----

    @Suppress("DEPRECATION")
    @Test
    fun switcherRowMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FunnelSwitcherRow(
                        funnels = listOf(funnel("shelves", "Shelves"), funnel("signup", "Signup"), funnel("purchase", "Purchase")),
                        currentKey = "shelves",
                        onSelect = {},
                        modifier = Modifier.width(900.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("funnels/switcher-normal.png")
    }

    // ---- 3 stat cards ----

    @Suppress("DEPRECATION")
    @Test
    fun cardsNormalMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FunnelStatCardsRow(
                        results = results(entered = 200, converted = 80, overallConversion = 0.4),
                        sizeClass = sizeClass,
                        loading = false,
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("funnels/cards-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun cardsLoadingMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FunnelStatCardsRow(results = null, sizeClass = sizeClass, loading = true)
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("funnels/cards-loading.png")
    }

    // ---- Step list + drop-off callouts (Task 28) ----

    private fun resultStep(key: String, name: String?, count: Int, fromEntry: Double, fromPrev: Double, dropped: Int, dropRate: Double) =
        FunnelStepResultDto(
            key = key,
            name = name,
            count = count,
            conversionFromEntry = fromEntry,
            conversionFromPrevious = fromPrev,
            dropped = dropped,
            dropRate = dropRate,
        )

    private val threeStepFunnel = listOf(
        resultStep("screen_view", "View signup", count = 200, fromEntry = 1.0, fromPrev = 0.0, dropped = 0, dropRate = 0.0),
        resultStep("form_started", "Start form", count = 120, fromEntry = 0.6, fromPrev = 0.6, dropped = 80, dropRate = 0.4),
        resultStep("signup_submitted", null, count = 80, fromEntry = 0.4, fromPrev = 0.667, dropped = 40, dropRate = 0.333),
    )

    @Suppress("DEPRECATION")
    @Test
    fun stepListMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    androidx.compose.runtime.CompositionLocalProvider(com.quietmetrix.dashboard.ui.components.LocalAiAccess provides com.quietmetrix.dashboard.api.AiCapabilities(true, true)) {
                        FunnelStepList(threeStepFunnel, modifier = Modifier.width(820.dp))
                    }
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("funnels/step-list-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun stepListShowsTheDropoffCalloutsButNotBeforeTheFirstStep() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    androidx.compose.runtime.CompositionLocalProvider(com.quietmetrix.dashboard.ui.components.LocalAiAccess provides com.quietmetrix.dashboard.api.AiCapabilities(true, true)) {
                        FunnelStepList(threeStepFunnel, modifier = Modifier.width(820.dp))
                    }
                }
            }
        }
        waitForIdle()
        // Exactly one callout per step with dropped > 0 (steps 2 and 3) — none for the entry step.
        onNodeWithText("80 people left here (40%)").assertExists()
        onNodeWithText("40 people left here (33%)").assertExists()
        onAllNodesWithText("Why did they leave?").assertCountEquals(2)
    }

    @Suppress("DEPRECATION")
    @Test
    fun stepListShowsNoDropoffCalloutWhenEveryStepFullyConverts() = runComposeUiTest {
        val noDrop = listOf(
            resultStep("a", "A", count = 50, fromEntry = 1.0, fromPrev = 0.0, dropped = 0, dropRate = 0.0),
            resultStep("b", "B", count = 50, fromEntry = 1.0, fromPrev = 1.0, dropped = 0, dropRate = 0.0),
        )
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FunnelStepList(noDrop, modifier = Modifier.width(820.dp))
                }
            }
        }
        waitForIdle()
        onAllNodesWithText("people left here", substring = true).assertCountEquals(0)
    }

    // ---- Expert-mode raw table CSV copy (Task 28) ----

    @Suppress("DEPRECATION")
    @Test
    fun csvCopyButtonShowsCopiedConfirmation() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    CsvCopyButton(csv = funnelStepsToCsv(threeStepFunnel))
                }
            }
        }
        waitForIdle()
        onNodeWithText("Copy as CSV").performClick()
        waitForIdle()
        onNodeWithText("Copied").assertExists()
    }
}
