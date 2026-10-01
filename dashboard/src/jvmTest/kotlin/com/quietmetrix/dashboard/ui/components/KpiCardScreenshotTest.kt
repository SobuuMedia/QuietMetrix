package com.quietmetrix.dashboard.ui.components

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
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for the restyled [KpiCard] (20dp radius, lime
 * delta pill, caption, lavender compare note). The vitals row in
 * `_source/01-overview-normal.png` sits below the Ask-drawer-covered
 * frame's fold (see `goldens/README.md`'s log for Task 9's identical
 * finding), so this golden is derived from the already-locked design
 * tokens + the HTML prototype's `vitals` card markup/README prose rather
 * than a JPEG crop, per the standing fallback ruling.
 */
@OptIn(ExperimentalTestApi::class)
class KpiCardScreenshotTest {

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun withDeltaCaptionAndCompareNoteMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                ) {
                    KpiCard(
                        label = "People",
                        value = "128,041",
                        delta = "▲ 18%",
                        deltaPositive = true,
                        sub = "About a full cinema, every day 🍿",
                        compareNote = "was 108,510 last week",
                        modifier = Modifier.width(280.dp),
                    )
                }
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("kpi-card/with-delta-and-compare.png")
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun clickableCardAtRestMatchesSameGoldenAsStaticCard() = runComposeUiTest {
        // Regression check for the hover/press idiom (KpiCard's KDoc):
        // passing onClick switches KpiCard onto the Card(onClick=...)
        // overload with the MutableInteractionSource-driven scale/
        // elevation/brighten animations wired up, but at rest (not
        // hovered, not pressed) it must render pixel-identical to the
        // static branch — same golden as the no-onClick test above.
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                ) {
                    KpiCard(
                        label = "People",
                        value = "128,041",
                        delta = "▲ 18%",
                        deltaPositive = true,
                        sub = "About a full cinema, every day 🍿",
                        compareNote = "was 108,510 last week",
                        modifier = Modifier.width(280.dp),
                        onClick = {},
                    )
                }
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("kpi-card/with-delta-and-compare.png")
    }
}
