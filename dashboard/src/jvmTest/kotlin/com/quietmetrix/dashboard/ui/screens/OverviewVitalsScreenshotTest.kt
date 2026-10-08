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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.DailyPoint
import com.quietmetrix.dashboard.api.RetentionCohort
import com.quietmetrix.dashboard.api.RetentionResponse
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.Totals
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 19's Overview vitals grid (`OverviewScreen.kt`'s
 * `VitalsSection`): 4 `KpiCard`s (People, Time per visit, Came back next day, Crashes) + the
 * optional lavender filter chip, derived per `goldens/README.md`'s "How a task derives its own
 * golden" process.
 *
 * The dated handoff screenshots show these cards, but include dashboard chrome and a prototype
 * state bar over part of the row. These focused renders are implementation regression baselines;
 * full-screen parity is checked separately against the full-resolution handoff references.
 *
 * Normal and Errors share one golden (`vitals/grid-normal.png`): `VitalsSection` has no
 * server-reachability awareness of its own (same as the pre-existing legacy KPI rows it
 * replaces) — the header-level `ServerUnreachableBanner` (Task 17) already owns communicating the
 * connection problem, and the vitals numbers themselves are rendered as whatever's currently
 * cached in `DashboardState`, stale or not, exactly like every other data card on this screen. The
 * "errors" test asserts against the same golden deliberately, the same pattern
 * `KpiCardScreenshotTest.clickableCardAtRestMatchesSameGoldenAsStaticCard` already uses when two
 * scenarios are expected to render pixel-identically.
 */
@OptIn(ExperimentalTestApi::class)
class OverviewVitalsScreenshotTest {

    private val sizeClass = WindowSizeClass.Expanded

    private val loadedAgg = AggregatesResponse(people = 1284, totals = Totals(events = 8734, errors = 12))
    private val loadedSess = SessionsResponse(
        totalSessions = 1284,
        avgDurationSec = 185,
        dailySessions = listOf(
            DailyPoint(day = "d1", total = 150),
            DailyPoint(day = "d2", total = 160),
            DailyPoint(day = "d3", total = 190),
            DailyPoint(day = "d4", total = 210),
        ),
    )
    private val loadedRetention = RetentionResponse(
        cohorts = listOf(RetentionCohort(cohortDate = "2026-09-20", size = 500, day1 = 0.42)),
    )

    @Suppress("DEPRECATION")
    @Test
    fun normalStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    VitalsSection(
                        agg = loadedAgg,
                        sess = loadedSess,
                        retention = loadedRetention,
                        loading = false,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(900.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/vitals/grid-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun loadingStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    VitalsSection(
                        agg = null,
                        sess = null,
                        retention = null,
                        loading = true,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(900.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/vitals/grid-loading.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun emptyStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    VitalsSection(
                        agg = AggregatesResponse(),
                        sess = SessionsResponse(totalSessions = 0),
                        retention = null,
                        loading = false,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(900.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/vitals/grid-empty.png")
    }

    // Deliberately asserts against the SAME golden as normalStateMatchesGolden — see this
    // class's KDoc for why VitalsSection has no distinct Errors-state rendering.
    @Suppress("DEPRECATION")
    @Test
    fun errorsStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    VitalsSection(
                        agg = loadedAgg,
                        sess = loadedSess,
                        retention = loadedRetention,
                        loading = false,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(900.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/vitals/grid-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun withActiveFilterChipMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    VitalsSection(
                        agg = loadedAgg,
                        sess = loadedSess,
                        retention = loadedRetention,
                        loading = false,
                        sizeClass = sizeClass,
                        activeFilter = "iOS",
                        modifier = Modifier.width(900.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/vitals/grid-with-filter.png")
    }
}
