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
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.ScreenDuration
import com.quietmetrix.dashboard.api.TopScreen
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 21's Overview 3-card row (`OverviewScreen.kt`'s
 * `ScreensPlatformsCountriesSection`): "📍 Where people hang out" / "📱 What they use" /
 * "🌍 Where they are", derived per `goldens/README.md`'s "How a task derives its own golden"
 * process.
 *
 * Real-crop derivation checked and REJECTED for all states, same finding `OverviewVitalsScreenshotTest`/
 * `OverviewDailyVolumeScreenshotTest` already recorded: this row sits below the daily-volume chart,
 * which itself sits below the vitals grid and the hero, all beneath every `_source/0{1,2,3,4}-
 * overview-*.png` frame's 540px-tall fold (the hero's own text is already cut off mid-line at the
 * bottom of `01-overview-normal.png`). Every golden here is therefore token/prototype/README-derived,
 * built from the real composable — same standing fallback as the other Overview section goldens.
 */
@OptIn(ExperimentalTestApi::class)
class OverviewTopListsScreenshotTest {

    private val sizeClass = WindowSizeClass.Expanded

    private val loadedAgg = AggregatesResponse(
        topScreens = listOf(
            TopScreen(screen = "home", count = 420),
            TopScreen(screen = "search", count = 180),
            TopScreen(screen = "settings", count = 60),
        ),
        screenDurations = listOf(
            ScreenDuration(screen = "home", avgMs = 185_000),
            ScreenDuration(screen = "search", avgMs = 8_000),
        ),
        platforms = listOf(BreakdownItem("android", 540), BreakdownItem("ios", 380), BreakdownItem("web", 80)),
        countries = listOf(BreakdownItem("Spain", 410), BreakdownItem("Mexico", 180), BreakdownItem("Argentina", 110)),
    )

    @Suppress("DEPRECATION")
    @Test
    fun normalStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ScreensPlatformsCountriesSection(
                        agg = loadedAgg,
                        loading = false,
                        sizeClass = sizeClass,
                        activeFilter = null,
                        onSelectFilter = { _, _ -> },
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/top-lists/row-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun loadingStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ScreensPlatformsCountriesSection(
                        agg = null,
                        loading = true,
                        sizeClass = sizeClass,
                        activeFilter = null,
                        onSelectFilter = { _, _ -> },
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/top-lists/row-loading.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun emptyStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ScreensPlatformsCountriesSection(
                        agg = AggregatesResponse(),
                        loading = false,
                        sizeClass = sizeClass,
                        activeFilter = null,
                        onSelectFilter = { _, _ -> },
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/top-lists/row-empty.png")
    }

    // Deliberately asserts against the SAME golden as normalStateMatchesGolden — this section has
    // no server-reachability awareness of its own, matching VitalsSection/DailyVolumeSection's own
    // documented convention (see those classes' KDoc).
    @Suppress("DEPRECATION")
    @Test
    fun errorsStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ScreensPlatformsCountriesSection(
                        agg = loadedAgg,
                        loading = false,
                        sizeClass = sizeClass,
                        activeFilter = null,
                        onSelectFilter = { _, _ -> },
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/top-lists/row-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun withActiveFilterHighlightsTheSelectedRowGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ScreensPlatformsCountriesSection(
                        agg = loadedAgg,
                        loading = false,
                        sizeClass = sizeClass,
                        activeFilter = "home",
                        onSelectFilter = { _, _ -> },
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/top-lists/row-with-filter.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun clickingAScreenRowInvokesOnSelectFilterWithItsName() = runComposeUiTest {
        var selected: String? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ScreensPlatformsCountriesSection(
                        agg = loadedAgg,
                        loading = false,
                        sizeClass = sizeClass,
                        activeFilter = null,
                        onSelectFilter = { _, value -> selected = value },
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("home").performClick()
        waitForIdle()
        kotlin.test.assertEquals("home", selected)
    }

    @Suppress("DEPRECATION")
    @Test
    fun clickingAPlatformRowInvokesOnSelectFilterWithItsName() = runComposeUiTest {
        var selected: String? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ScreensPlatformsCountriesSection(
                        agg = loadedAgg,
                        loading = false,
                        sizeClass = sizeClass,
                        activeFilter = null,
                        onSelectFilter = { _, value -> selected = value },
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("android").performClick()
        waitForIdle()
        kotlin.test.assertEquals("android", selected)
    }
}
