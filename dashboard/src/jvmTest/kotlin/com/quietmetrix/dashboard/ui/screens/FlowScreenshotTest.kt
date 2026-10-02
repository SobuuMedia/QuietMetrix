package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.quietmetrix.dashboard.api.Transition
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Screenshot-golden coverage for Task 22's Flow screen extraction (`FlowScreen.kt`): the new
 * header (`FlowHero` + `PlatformFilterRow`) and the breadcrumb (`FlowBreadcrumbSection`), derived
 * per `goldens/README.md`'s "How a task derives its own golden" process.
 *
 * **Header region** (`flow/header.png`): real-crop-derived from `_source/05-flow-normal.png` at
 * `(0,226,465,479)` — the "🧭 Flow" eyebrow, the two-line H1 with its lime-highlighted "where they
 * go next" span (cut off mid-word by the Ask-drawer overlay at this frame's x≈465, same finding
 * every other Flow/Overview header golden records), and the "Platform" row with its 4 pills
 * ("🌈 All" rendered active — `--ink` bg/`--bg` text per the prototype's default `pill()` style,
 * i.e. `PillVariant.NavActive`, NOT the lime accent `pill(on, '#c8f25a')` only the "Start from" row
 * uses — "🤖 Android"/"🍎 iOS" visible, "🌐 Web" cut off past x≈465) are all visible and verified
 * by direct PIL crop before recording. Rendered via `QuietMetrixTheme(Dark)` at the default
 * 1024x768 `runComposeUiTest` canvas, content width-constrained to `Modifier.width(465.dp)` to
 * match the source rect, recorded with `-Pgolden.record=true`.
 *
 * **Breadcrumb region** (`flow/breadcrumb-*.png`): real-crop derivation checked and REJECTED —
 * the breadcrumb card sits below the Platform row, entirely covered by the pinned-open Ask-drawer
 * overlay AND the "MOCKUP ONLY" toolbar strip in `_source/05-flow-normal.png` (same rejection
 * pattern every covered-region Overview golden already records). Derived instead from the design
 * prototype's own Flow markup (`QuietMetrix Prototype.dc.html`'s `flowCols`/`edgesOf`) for the
 * column/bar/exit-row layout, scoped down per `FlowPathLogic.kt`'s top-of-file data-honesty KDoc:
 * each column reports its screen's own real outgoing-transition total, never a fabricated
 * cross-hop "people" figure.
 */
@OptIn(ExperimentalTestApi::class)
class FlowScreenshotTest {

    private val sampleTransitions = listOf(
        Transition(fromScreen = "home", toScreen = "search", count = 60),
        Transition(fromScreen = "home", toScreen = "settings", count = 40),
        Transition(fromScreen = "search", toScreen = "results", count = 30),
        Transition(fromScreen = "search", toScreen = null, count = 10),
    )

    @Suppress("DEPRECATION")
    @Test
    fun headerMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    Column(modifier = Modifier.width(465.dp)) {
                        FlowHero()
                        PlatformFilterRow(modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("flow/header.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun breadcrumbEmptyPathMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreadcrumbSection(
                        path = emptyList(),
                        transitions = sampleTransitions,
                        onPick = {},
                        modifier = Modifier.width(700.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("flow/breadcrumb-empty.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun breadcrumbWithPathMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreadcrumbSection(
                        path = listOf("home", "search"),
                        transitions = sampleTransitions,
                        onPick = {},
                        modifier = Modifier.width(700.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("flow/breadcrumb-with-path.png")
    }

    // -- behavioral coverage (not golden-gated) -----------------------------------------

    @Suppress("DEPRECATION")
    @Test
    fun clickingANonExitOptionExtendsThePathViaOnPick() = runComposeUiTest {
        var picked: FlowStepOption? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreadcrumbSection(
                        path = listOf("home"),
                        transitions = sampleTransitions,
                        onPick = { picked = it },
                        modifier = Modifier.width(700.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("search").performClick()
        waitForIdle()
        assertEquals("search", picked?.screen)
    }

    @Suppress("DEPRECATION")
    @Test
    fun theExitRowHasNoClickHandlerWired() = runComposeUiTest {
        var picked: FlowStepOption? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreadcrumbSection(
                        path = listOf("search"),
                        transitions = sampleTransitions,
                        onPick = { picked = it },
                        modifier = Modifier.width(700.dp),
                    )
                }
            }
        }
        waitForIdle()
        // The 👋 exit row ("search" -> null edge) renders its label but — unlike the clickable
        // "results" row above it in the same column — has no `clickable` modifier at all (design
        // handoff README > Flow: "not clickable"). This test harness dispatches `performClick()`
        // as a raw pointer event at the node's position rather than invoking a semantics action,
        // so the tap itself never throws; the real assertion is that it has nothing to hit —
        // [onPick] never fires for it.
        onNodeWithText("👋 Left the app").performClick()
        waitForIdle()
        assertEquals(null, picked)
    }
}
