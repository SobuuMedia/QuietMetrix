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
import com.quietmetrix.dashboard.api.SessionsResponse
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.TopEvent
import com.quietmetrix.dashboard.api.Totals
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 18's Overview hero + privacy card (`OverviewScreen.kt`'s
 * `OverviewHero`/`PrivacyCard`), derived per `goldens/README.md`'s "How a task derives its own
 * golden" process.
 *
 * Hero region, all 4 `_source/0{1,2,3,4}-overview-*.png`: real-crop-derived for text content at
 * `(0,226,465,248)` for the Normal/Loading/Empty frames and `(0,226,448,248)` for the Errors frame
 * (narrower — that frame's Ask-drawer overlay edge sits further left, same finding already
 * recorded for `header/server-unreachable-banner.png`), all below the nav/progress-bar strip and
 * left of the Ask-drawer overlay:
 * - Normal: the source frame shows "Good afternoon, Sobuu team 👋 Here's your week, Sep 18 – 24."
 *   + "This week **1,284 people** opened Sobuu **18% more**...", but the rendered copy here reads
 *   "**1,284 sessions** opened Sobuu, **trending up this period**." — relabeled per a post-review
 *   decision (see Task 18's report "Relabeling" section): "people" would claim a distinct-device
 *   count this dashboard doesn't track (it's actually total sessions), and a numeric "18% more"
 *   would claim a real previous-period comparison this dashboard can't make (it's actually a
 *   same-window momentum signal) — both are now worded honestly instead of visually confident but
 *   misleading, per `OverviewHeroLogic.kt`'s KDoc. The lime `SpanStyle` highlight treatment itself
 *   still matches the source's two highlighted phrases. The golden is a self-consistent
 *   regression baseline recorded from the real composable, per the standing fallback ruling
 *   (Task 9's `pill/variants.png` entry), not a pixel-match to the JPEG.
 * - Loading: "🧮 Counting your people…" verbatim — a whimsical loading message, not a data claim,
 *   so it wasn't in scope for the "people"/"% more" relabeling above (no number is asserted here).
 * - Empty: "👋 Welcome to Sobuu" + "No data yet. Let's get your **first event** in about 5
 *   minutes." verbatim (lime span on "first event").
 * - Errors: greeting line + plain "Here are your numbers for ..." headline verbatim; the "😴 The
 *   AI summary is taking a nap" notice sits below this crop's fold (cut off), so its
 *   presence/copy is spec-derived (the HTML prototype's `err` branch,
 *   `design_handoff_dashboard_redesign/QuietMetrix Prototype.dc.html:79`) rather than crop-derived.
 *
 * Privacy card: sits in the right column of `_source/01-overview-normal.png`, entirely covered by
 * the pinned-open Ask-drawer overlay in every `_source/` frame (same finding as Task 9's
 * `pill/variants.png` entry) — `privacy-card.png` is fully token/prototype/README-derived, not a
 * `_source/` crop.
 */
@OptIn(ExperimentalTestApi::class)
class OverviewHeroScreenshotTest {

    private val project = "Sobuu"
    private val range = TimeRange.Day7

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's reviewed convention,
    // commit ab300e0): v1 runComposeUiTest is what the existing screenshot-golden infra is
    // built on.
    @Suppress("DEPRECATION")
    @Test
    fun normalStateMatchesGolden() = runComposeUiTest {
        val agg = AggregatesResponse(
            totals = Totals(events = 8734),
            daily = listOf(
                DailyPoint(day = "d1", total = 900),
                DailyPoint(day = "d2", total = 950),
                DailyPoint(day = "d3", total = 1000),
                DailyPoint(day = "d4", total = 1200),
                DailyPoint(day = "d5", total = 1300),
                DailyPoint(day = "d6", total = 1350),
                DailyPoint(day = "d7", total = 1300),
            ),
            topEvents = listOf(TopEvent(eventName = "app_open", count = 4200)),
        )
        val sess = SessionsResponse(totalSessions = 1284)
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    OverviewHero(
                        projectName = project,
                        range = range,
                        agg = agg,
                        sess = sess,
                        loading = false,
                        serverUnreachable = false,
                        modifier = Modifier.width(640.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/hero-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun loadingStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    OverviewHero(
                        projectName = project,
                        range = range,
                        agg = null,
                        sess = null,
                        loading = true,
                        serverUnreachable = false,
                        modifier = Modifier.width(640.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/hero-loading.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun emptyStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    OverviewHero(
                        projectName = project,
                        range = range,
                        agg = AggregatesResponse(),
                        sess = SessionsResponse(totalSessions = 0),
                        loading = false,
                        serverUnreachable = false,
                        modifier = Modifier.width(640.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/hero-empty.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun errorsStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    OverviewHero(
                        projectName = project,
                        range = range,
                        agg = AggregatesResponse(totals = Totals(events = 8734)),
                        sess = SessionsResponse(totalSessions = 1284),
                        loading = false,
                        serverUnreachable = true,
                        modifier = Modifier.width(640.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/hero-errors.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun privacyCardMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    PrivacyCard(modifier = Modifier.width(320.dp))
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("overview/privacy-card.png")
    }
}
