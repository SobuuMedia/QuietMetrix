package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** [androidx.compose.ui.platform.testTag] used by the hover/press tests below to target the clickable card directly. */
private const val KPI_CARD_TAG = "kpiCard"

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

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun hoveringClickableCardChangesRenderedPixels() = runComposeUiTest {
        // Direct coverage for the hover half of the hover/press idiom
        // (KpiCard's KDoc): actually drives pointer-enter on the card's
        // node via performMouseInput and asserts the captured frame
        // differs from the resting-state capture — catching a future edit
        // that silently dropped or inverted the hover-brighten animation,
        // which neither of the resting-state golden tests above could
        // ever detect (they never move the pointer).
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
                        modifier = Modifier.width(280.dp).testTag(KPI_CARD_TAG),
                        onClick = {},
                    )
                }
            }
        }
        waitForIdle()
        val resting = onRoot().captureToImage()

        onNodeWithTag(KPI_CARD_TAG).performMouseInput { enter(center) }
        waitForIdle()
        val hovered = onRoot().captureToImage()

        assertTrue(
            hovered.differsFrom(resting),
            "Hovering the card produced no pixel change — the hover-brighten animation " +
                "(animateColorAsState reading collectIsHoveredAsState()) may be broken.",
        )
    }

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun pressingClickableCardChangesRenderedPixels() = runComposeUiTest {
        // Direct coverage for the press half of the hover/press idiom:
        // drives a mouse-button-down (without release, so the gesture is
        // still "held") on the card's node and asserts the captured frame
        // differs from a capture taken immediately before the press.
        //
        // The "before" baseline here is the *hovered* (not fully resting)
        // state: press() requires the pointer to already be positioned
        // over the node via enter()/moveTo() first (confirmed empirically
        // — press() alone, with no prior enter, did not register as a
        // press on this node), and that enter() alone already changes
        // pixels (see hoveringClickableCardChangesRenderedPixels). Diffing
        // the pressed capture against a fully-resting one would really be
        // asserting "hovered+pressed differs from resting", which stays
        // true from the hover-brighten animation alone even if the
        // press-scale animation were deleted entirely — it wouldn't
        // isolate press from hover. Comparing pressed-after-hover against
        // hovered-before-press isolates the press-specific delta instead.
        //
        // Note this still can't attribute that delta to PRESS_SCALE alone
        // in isolation: Card(onClick = ...) also draws Material3's
        // built-in ripple indication on press (hardcoded inside
        // Surface(onClick = ...), not exposed as a parameter on this Card
        // overload, so it can't be disabled from here) — confirmed by
        // temporarily zeroing PRESS_SCALE locally and re-running this
        // test, which still passed on the ripple alone. So this is a
        // "press has its own visible effect, distinct from hover" smoke
        // check, not a PRESS_SCALE-only unit test; it would still catch
        // onClick/interactionSource being wired up incorrectly, but a
        // regression isolated to just PRESS_SCALE could in principle slip
        // through undetected here.
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
                        modifier = Modifier.width(280.dp).testTag(KPI_CARD_TAG),
                        onClick = {},
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag(KPI_CARD_TAG).performMouseInput { enter(center) }
        waitForIdle()
        val hoveredOnly = onRoot().captureToImage()

        onNodeWithTag(KPI_CARD_TAG).performMouseInput { press() }
        waitForIdle()
        val hoveredAndPressed = onRoot().captureToImage()

        assertTrue(
            hoveredAndPressed.differsFrom(hoveredOnly),
            "Pressing the card (on top of hover) produced no further pixel change — the " +
                "press-scale animation (animateFloatAsState reading " +
                "collectIsPressedAsState()) and/or Card's press ripple may be broken.",
        )
    }
}

/**
 * True if this bitmap and [other] (same dimensions) have at least one
 * pixel whose RGB differs by more than a small per-channel tolerance —
 * used by the hover/press tests above to assert an interaction actually
 * changed the rendered frame, without pinning down exactly which pixels or
 * by how much (that precision belongs to a golden-file comparison, not a
 * "did the animation fire at all" smoke check).
 */
private fun ImageBitmap.differsFrom(other: ImageBitmap, channelTolerance: Int = 2): Boolean {
    val a = this.toAwtImage()
    val b = other.toAwtImage()
    check(a.width == b.width && a.height == b.height) { "Compared bitmaps must share dimensions." }

    for (y in 0 until a.height) {
        for (x in 0 until a.width) {
            val pa = a.getRGB(x, y)
            val pb = b.getRGB(x, y)
            val dr = abs(((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF))
            val dg = abs(((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF))
            val db = abs((pa and 0xFF) - (pb and 0xFF))
            if (dr > channelTolerance || dg > channelTolerance || db > channelTolerance) return true
        }
    }
    return false
}
