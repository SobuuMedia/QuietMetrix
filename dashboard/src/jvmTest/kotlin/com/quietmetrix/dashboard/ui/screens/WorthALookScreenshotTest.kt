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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.TopScreen
import com.quietmetrix.dashboard.api.Totals
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Screenshot-golden + interaction coverage for Task 21's "Worth a look" card
 * (`OverviewScreen.kt`'s `WorthALookSection`), derived per `goldens/README.md`'s "How a task
 * derives its own golden" process.
 *
 * See `OverviewTopListsLogic.kt`'s `WorthALookObservation` KDoc for the data-honesty investigation
 * behind this card's content: unlike the design handoff's literal "3 auto-generated observations",
 * every observation rendered here is a single plain, mechanically-derived fact (a real offline
 * count, a real top screen, a real majority platform share) — never a fabricated trend/insight.
 *
 * Real-crop derivation checked and REJECTED for all states — same finding as
 * `OverviewTopListsScreenshotTest` one card up (sits below the chart, below the fold in every
 * `_source/` frame).
 */
@OptIn(ExperimentalTestApi::class)
class WorthALookScreenshotTest {

    private val aggWithAllThreeObservations = AggregatesResponse(
        totals = Totals(offline = 612),
        topScreens = listOf(TopScreen(screen = "home", count = 420), TopScreen(screen = "search", count = 90)),
        platforms = listOf(BreakdownItem("android", 600), BreakdownItem("ios", 400)),
    )

    @Suppress("DEPRECATION")
    @Test
    fun normalStateWithObservationsMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    WorthALookSection(
                        agg = aggWithAllThreeObservations,
                        dismissedIds = emptySet(),
                        loading = false,
                        onDismiss = {},
                        onSnooze = {},
                        modifier = Modifier.width(420.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/worth-a-look/card-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun loadingStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    WorthALookSection(
                        agg = null,
                        dismissedIds = emptySet(),
                        loading = true,
                        onDismiss = {},
                        onSnooze = {},
                        modifier = Modifier.width(420.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/worth-a-look/card-loading.png")
    }

    // "Empty" here means no real observation holds (not the whole-screen first-run Empty state,
    // which swaps this whole card out for SetupInstructionsSection) — the honest "🎈 All caught
    // up!" placeholder (design_handoff_dashboard_redesign/README.md > Overview: "when the list is
    // empty it shows a '🎈 All caught up!' placeholder").
    @Suppress("DEPRECATION")
    @Test
    fun noObservationsShowsAllCaughtUpGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    WorthALookSection(
                        agg = AggregatesResponse(),
                        dismissedIds = emptySet(),
                        loading = false,
                        onDismiss = {},
                        onSnooze = {},
                        modifier = Modifier.width(420.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/worth-a-look/card-all-caught-up.png")
    }

    // Deliberately asserts against the SAME golden as normalStateWithObservationsMatchesGolden —
    // this card has no server-reachability awareness of its own, same convention as
    // VitalsSection/DailyVolumeSection/ScreensPlatformsCountriesSection.
    @Suppress("DEPRECATION")
    @Test
    fun errorsStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    WorthALookSection(
                        agg = aggWithAllThreeObservations,
                        dismissedIds = emptySet(),
                        loading = false,
                        onDismiss = {},
                        onSnooze = {},
                        modifier = Modifier.width(420.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/worth-a-look/card-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun dismissedIdsAreFilteredOutOfTheRenderedList() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    WorthALookSection(
                        agg = aggWithAllThreeObservations,
                        dismissedIds = setOf("offline_sync", "top_screen"),
                        loading = false,
                        onDismiss = {},
                        onSnooze = {},
                        modifier = Modifier.width(420.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/worth-a-look/card-partially-dismissed.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun dismissButtonInvokesOnDismissWithTheObservationId() = runComposeUiTest {
        var dismissedId: String? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    WorthALookSection(
                        agg = aggWithAllThreeObservations,
                        dismissedIds = emptySet(),
                        loading = false,
                        onDismiss = { dismissedId = it },
                        onSnooze = {},
                        modifier = Modifier.width(420.dp),
                    )
                }
            }
        }
        waitForIdle()
        onAllNodesWithText("✕")[0].performClick()
        waitForIdle()
        assertEquals("offline_sync", dismissedId)
    }

    @Suppress("DEPRECATION")
    @Test
    fun snoozeButtonInvokesOnSnoozeWithTheObservationId() = runComposeUiTest {
        var snoozedId: String? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    WorthALookSection(
                        agg = aggWithAllThreeObservations,
                        dismissedIds = emptySet(),
                        loading = false,
                        onDismiss = {},
                        onSnooze = { snoozedId = it },
                        modifier = Modifier.width(420.dp),
                    )
                }
            }
        }
        waitForIdle()
        onAllNodesWithText("💤")[0].performClick()
        waitForIdle()
        assertEquals("offline_sync", snoozedId)
    }
}
