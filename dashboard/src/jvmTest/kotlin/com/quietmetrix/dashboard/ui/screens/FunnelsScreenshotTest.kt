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
import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import com.quietmetrix.dashboard.api.FunnelSummaryDto
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 27's Funnels restyle (`FunnelsScreen.kt`): the new H1
 * headline (`FunnelHero`), the funnel-switcher pill row + "＋ New funnel" stub button
 * (`FunnelSwitcherRow`), and the 3 stat cards (`FunnelStatCardsRow`), derived per
 * `goldens/README.md`'s "How a task derives its own golden" process.
 *
 * **Hero region** (`funnels/hero-*.png`): real-crop-derived from `_source/07-funnels-normal.png`
 * — the "🪜 Funnels" eyebrow and the full 3-line H1 are genuinely visible, unobscured by the
 * Ask-drawer overlay (which starts at x≈465 in this frame), confirmed by a standalone PIL crop
 * before writing this test (same process as `retention/hero-*.png`'s Task 24 entry). That crop's
 * literal wording — "Out of 10 people who open Shelves, 4 end up adding a book." — is NOT what
 * `FunnelHero` renders; see that composable's KDoc for the data-honesty investigation (turning a
 * funnel's step event names into an English verb phrase is a fabrication risk, the same category
 * already corrected for in `OverviewHeroLogic.kt`). The rendered copy instead names the funnel by
 * its own real `FunnelDto.name`, keeping the exact same "out of 10, lime-highlighted number"
 * visual shape the crop confirms (eyebrow emoji, 3-line wrap width, `displayLarge` sizing, lime
 * `SpanStyle` highlight). Rendered via `QuietMetrixTheme(Dark)` at the default 1024x768
 * `runComposeUiTest` canvas, content `Modifier.width(465.dp)` to match the source rect, recorded
 * with `-Pgolden.record=true`.
 *
 * **Switcher row + stat cards regions** (`funnels/switcher-*.png`, `funnels/cards-*.png`):
 * real-crop derivation checked and REJECTED — both sit below the H1 in
 * `_source/07-funnels-normal.png` and are entirely covered by the "MOCKUP ONLY" state-switcher
 * toolbar strip glued to the bottom of that 909x540 frame (confirmed by a zoomed crop of the
 * (0,450,465,90) region before writing this test: only the toolbar's own "Normal/Loading/Empty/
 * Errors" buttons are visible there, no real page content) — same rejection pattern as
 * `retention/cards-normal.png`'s Task 24 entry. Derived instead from the HTML prototype's own
 * funnels markup (`design_handoff_dashboard_redesign/QuietMetrix Prototype.dc.html` lines 288-298:
 * the "＋ New funnel" pill, the `funnelTabs` pill row, and the 3 stat-card `div`s — "🚪 Started",
 * "🏁 Made it to the end", a lime-filled "🎯 Success rate") + the already-locked `Pill`/`KpiCard`
 * tokens. Rendered via `QuietMetrixTheme(Dark)` at the default 1024x768 canvas,
 * `Modifier.width(900.dp)`, `WindowSizeClass.Expanded` for the cards row, recorded with
 * `-Pgolden.record=true`.
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
    fun heroNormalMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FunnelHero(
                        funnel = funnel("shelves", "Shelves"),
                        results = results(entered = 200, converted = 80, overallConversion = 0.4),
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

    // ---- Switcher pill row + "＋ New funnel" ----

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
}
