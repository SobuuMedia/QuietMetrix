package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.DailyPoint
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 20's Overview daily-volume chart card
 * (`OverviewScreen.kt`'s `DailyVolumeSection`), derived per `goldens/README.md`'s "How a task
 * derives its own golden" process.
 *
 * Real-crop derivation checked and REJECTED for all states, same finding `OverviewVitalsScreenshotTest`
 * already recorded for the vitals grid: the chart card sits below the vitals grid, which itself
 * sits below the hero in every `_source/0{1,2,3,4}-overview-*.png` frame (909x540 JPEGs) — the
 * hero's own text already wraps past the 540px-tall fold (visually confirmed: "...18% more than
 * last week 🚀" is cut off mid-line at the very bottom of `01-overview-normal.png`), so the chart
 * card is never visible in any `_source/` frame. Every golden here is therefore token/prototype/
 * README-derived, built from the real composable — same standing fallback as
 * `overview/vitals/grid-*.png` (Task 19) and `pill/variants.png` (Task 9).
 *
 * Fixture data is deliberately shaped so one bucket is a clear, unique maximum (the "record day"),
 * exercising [recordDayIndex]'s real highlight + caption; `tiedDataStateMatchesGolden` instead uses
 * all-equal values to exercise the honest no-highlight/no-caption path (see `OverviewChartLogic.kt`'s
 * `recordDayIndex` KDoc for why a tied series shows no callout).
 */
@OptIn(ExperimentalTestApi::class)
class OverviewDailyVolumeScreenshotTest {

    private val aggWithRecordDay = AggregatesResponse(
        daily = listOf(
            DailyPoint(day = "2026-09-20", total = 120),
            DailyPoint(day = "2026-09-21", total = 95),
            DailyPoint(day = "2026-09-22", total = 310),
            DailyPoint(day = "2026-09-23", total = 140),
        ),
        dailyPeople = listOf(
            DailyPoint(day = "2026-09-20", total = 22),
            DailyPoint(day = "2026-09-21", total = 25),
            DailyPoint(day = "2026-09-22", total = 37),
            DailyPoint(day = "2026-09-23", total = 31),
        ),
    )
    private val sessWithRecordDay = SessionsResponse(
        totalSessions = 1284,
        dailySessions = listOf(
            DailyPoint(day = "2026-09-20", total = 150),
            DailyPoint(day = "2026-09-21", total = 160),
            DailyPoint(day = "2026-09-22", total = 190),
            DailyPoint(day = "2026-09-23", total = 420),
        ),
    )

    @Test
    fun peopleVisitsActionsTabsStayVisibleOnPhoneAndTablet() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    DailyVolumeSection(
                        agg = aggWithRecordDay.copy(dailyPeople = aggWithRecordDay.daily),
                        sess = sessWithRecordDay,
                        range = TimeRange.Day7,
                        sizeClass = WindowSizeClass.Compact,
                        modifier = Modifier.width(328.dp),
                    )
                }
            }
        }
        listOf("Who showed up each day", "People", "Visits", "Actions").forEach { onNodeWithText(it).assertIsDisplayed() }
        val phoneRight = onNodeWithText("Actions").fetchSemanticsNode().boundsInRoot.right
        assert(phoneRight <= 360f) { "Phone chart tab extends past its 360dp viewport: $phoneRight" }

        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    DailyVolumeSection(
                        agg = aggWithRecordDay.copy(dailyPeople = aggWithRecordDay.daily),
                        sess = sessWithRecordDay,
                        range = TimeRange.Day7,
                        sizeClass = WindowSizeClass.Medium,
                        modifier = Modifier.width(736.dp),
                    )
                }
            }
        }
        listOf("People", "Visits", "Actions").forEach { onNodeWithText(it).assertIsDisplayed() }
        val tabletRight = onNodeWithText("Actions").fetchSemanticsNode().boundsInRoot.right
        assert(tabletRight <= 768f) { "Tablet chart tab extends past its 768dp viewport: $tabletRight" }
    }

    @Suppress("DEPRECATION")
    @Test
    fun defaultPeopleStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    DailyVolumeSection(
                        agg = aggWithRecordDay,
                        sess = sessWithRecordDay,
                        range = TimeRange.Day7,
                        modifier = Modifier.width(600.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/daily-volume/chart-people.png")
    }

    // Starts on the default "People" selection and clicks the "Visits" segment, exercising both
    // the segmented control's interaction and the Visits
    // data path (SessionsResponse.dailySessions) in one golden.
    @Suppress("DEPRECATION")
    @Test
    fun switchingToVisitsStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    DailyVolumeSection(
                        agg = aggWithRecordDay,
                        sess = sessWithRecordDay,
                        range = TimeRange.Day7,
                        modifier = Modifier.width(600.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Visits").performClick()
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/daily-volume/chart-visits.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun emptyStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    DailyVolumeSection(
                        agg = AggregatesResponse(),
                        sess = SessionsResponse(totalSessions = 0),
                        range = TimeRange.Day7,
                        modifier = Modifier.width(600.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/daily-volume/chart-empty.png")
    }

    // All buckets tied at the same nonzero value: recordDayIndex honestly returns null, so no bar
    // is highlighted lime and no "Busiest day" caption appears — see OverviewChartLogic.kt's
    // recordDayIndex KDoc.
    @Suppress("DEPRECATION")
    @Test
    fun tiedDataStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    DailyVolumeSection(
                        agg = AggregatesResponse(
                            daily = listOf(
                                DailyPoint(day = "2026-09-20", total = 100),
                                DailyPoint(day = "2026-09-21", total = 100),
                                DailyPoint(day = "2026-09-22", total = 100),
                            ),
                            dailyPeople = listOf(
                                DailyPoint(day = "2026-09-20", total = 100),
                                DailyPoint(day = "2026-09-21", total = 100),
                                DailyPoint(day = "2026-09-22", total = 100),
                            ),
                        ),
                        sess = SessionsResponse(totalSessions = 300),
                        range = TimeRange.Day7,
                        modifier = Modifier.width(600.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/daily-volume/chart-tied.png")
    }
}
