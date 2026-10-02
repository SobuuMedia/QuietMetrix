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
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for Task 24's Retention screen extraction/addition (`RetentionScreen.kt`):
 * the new H1 headline (`RetentionHero`) and the 3 vital cards (`RetentionVitalCardsRow`), derived
 * per `goldens/README.md`'s "How a task derives its own golden" process.
 *
 * **Hero region** (`retention/hero-*.png`): the "🔁 Retention" eyebrow and the 3-line H1 are
 * real-crop-derived from `_source/06-retention-normal.png` at `(0,226,465,234)` — wording
 * ("Out of 10 new people" / "the next day and 2 ar[e]" / "week later.") and wrapping width are
 * genuinely visible there, confirmed by a standalone PIL crop before writing this test (same
 * process as `flow/header.png`'s Task 22 entry). The lime-highlighted number span itself ("4 come
 * back") sits past this frame's Ask-drawer overlay (x≈465) and is NOT visible in the crop — same
 * "no delta-pill rect" finding `pill/variants.png`'s Task 9 entry already recorded for a sibling
 * hero; the lime `SpanStyle` treatment is token-derived (`ExtendedColors.lime`/`.onLime`), matching
 * every other hero in this plan (`OverviewHeroNormal`, `FlowHero`). Rendered via
 * `QuietMetrixTheme(Dark)` at the default 1024x768 `runComposeUiTest` canvas, content
 * `Modifier.width(465.dp)` to match the source rect, recorded with `-Pgolden.record=true`.
 *
 * **3-card row region** (`retention/cards-*.png`): real-crop derivation checked and REJECTED —
 * the card row sits below the H1, covered by the pinned-open Ask-drawer overlay and cut off by the
 * "MOCKUP ONLY" toolbar strip in `_source/06-retention-normal.png` (909x540); only a sliver of the
 * first card's label and percentage survive at the very bottom edge, not enough to derive layout
 * from — same rejection pattern as `flow/cards-normal.png`'s Task 23 entry. Derived instead from
 * the design prototype's own card markup (`QuietMetrix Prototype.dc.html:246-250`: label + big %,
 * a 10-dot grid, one-line caption) + the already-locked design tokens. Rendered via
 * `QuietMetrixTheme(Dark)` at the default 1024x768 canvas, `Modifier.width(960.dp)`,
 * `WindowSizeClass.Expanded`, recorded with `-Pgolden.record=true`.
 *
 * **Data-honesty investigation** (full writeup in `RetentionLogic.kt`'s class/function KDocs):
 * Day-1/Day-7/Day-30 are all genuinely computed server-side by the identical `n/size` ratio
 * mechanism (`RetentionCounterAnalyzer.analyze`) — there is no real/placeholder split between them.
 * `cardsTooEarlyStateMatchesGolden` and `heroNotEnoughDataMatchesGolden` below exercise the honest
 * null-handling branches (a day not yet observed by any cohort), confirming the "too early" /
 * "not enough data yet" copy renders instead of a fabricated 0%.
 */
@OptIn(ExperimentalTestApi::class)
class RetentionScreenshotTest {

    private val sizeClass = WindowSizeClass.Expanded

    // ---- Hero (H1) ----

    @Suppress("DEPRECATION")
    @Test
    fun heroBothAvailableMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    RetentionHero(RetentionVitals(day1 = 0.42, day7 = 0.21, day30 = 0.09), modifier = Modifier.width(465.dp))
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("retention/hero-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun heroNotEnoughDataMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    RetentionHero(RetentionVitals(day1 = null, day7 = null, day30 = null), modifier = Modifier.width(465.dp))
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("retention/hero-not-enough-data.png")
    }

    // ---- 3-card row ----

    @Suppress("DEPRECATION")
    @Test
    fun cardsNormalStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    RetentionVitalCardsRow(
                        RetentionVitals(day1 = 0.42, day7 = 0.21, day30 = 0.09),
                        sizeClass,
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("retention/cards-normal.png")
    }

    // A brand-new project: no cohort is old enough to have observed any of the 3 days yet. Each
    // card must show the honest "too early" placeholder, never a fabricated 0%.
    @Suppress("DEPRECATION")
    @Test
    fun cardsTooEarlyStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    RetentionVitalCardsRow(
                        RetentionVitals(day1 = null, day7 = null, day30 = null),
                        sizeClass,
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("retention/cards-too-early.png")
    }
}
