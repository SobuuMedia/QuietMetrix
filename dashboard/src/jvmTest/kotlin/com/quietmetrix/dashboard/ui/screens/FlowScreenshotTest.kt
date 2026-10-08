package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.BreakdownItem
import com.quietmetrix.dashboard.api.Transition
import com.quietmetrix.dashboard.nav.WindowSizeClass
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
 * **Header region** (`flow/header.png`): the "🧭 Flow" eyebrow and the two-line H1 with its
 * lime-highlighted "where they go next" span are real-crop-derived from `_source/05-flow-normal.png`
 * at `(0,226,465,479)` (cut off mid-word by the Ask-drawer overlay at this frame's x≈465, same
 * finding every other Flow/Overview header golden records) — "🌈 All" rendered active (`--ink`
 * bg/`--bg` text per the prototype's default `pill()` style, i.e. `PillVariant.NavActive`, NOT the
 * lime accent `pill(on, '#c8f25a')` only the "Start from" row uses) also matches that crop. The
 * source frame's "🤖 Android"/"🍎 iOS" pills (before "🌐 Web" is cut off at x≈465) are deliberately
 * NOT reproduced here — see `PlatformFilterRow`'s KDoc (`FlowScreen.kt`) for the post-review finding
 * that a disabled `Pill` is visually indistinguishable from a live one elsewhere on this screen, and
 * the fix: only the one real option ("🌈 All") renders as a pill, replaced-3-pills' disclosure is a
 * single always-visible caption instead. Rendered via `QuietMetrixTheme(Dark)` at the default
 * 1024x768 `runComposeUiTest` canvas, content width-constrained to `Modifier.width(465.dp)` to
 * match the source rect's H1 wrapping, recorded with `-Pgolden.record=true`.
 *
 * **Breadcrumb region** (`flow/breadcrumb-*.png`): real-crop derivation checked and REJECTED —
 * the breadcrumb card sits below the Platform row, entirely covered by the pinned-open Ask-drawer
 * overlay AND the "MOCKUP ONLY" toolbar strip in `_source/05-flow-normal.png` (same rejection
 * pattern every covered-region Overview golden already records). Derived instead from the design
 * prototype's own Flow markup (`QuietMetrix Prototype.dc.html`'s `flowCols`/`edgesOf`) for the
 * column/bar/exit-row layout, scoped down per `FlowPathLogic.kt`'s top-of-file data-honesty KDoc:
 * each column reports its screen's own real outgoing-transition total, never a fabricated
 * cross-hop "people" figure.
 *
 * **3-card row region** (`flow/cards-*.png`, Task 23): real-crop derivation checked and REJECTED
 * — this row sits below the breadcrumb, which `breadcrumbEmptyMatchesGolden`'s own Notes entry
 * above already established is entirely covered by the Ask-drawer overlay and "MOCKUP ONLY"
 * toolbar strip in `_source/05-flow-normal.png` (909x540; the row would sit even lower, well past
 * both). Derived instead from the design prototype's own spec text (design handoff README > Flow:
 * "3-card row — Countries (bar list), Platforms (3 stat tiles + top OS versions), Device types
 * (phone/tablet/desktop progress bars)") and the real [FlowBreakdownCardsSection] composable — see
 * `FlowPathLogic.kt`'s Task 23 section KDoc for the data-honesty investigation behind what's real
 * (platforms) vs. honestly empty (countries, device types) vs. honestly omitted (OS versions).
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
    fun platformFilterSelectsAndReportsTheChosenPlatform() = runComposeUiTest {
        var selected = "all"
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            PlatformFilterRow(selected = selected, onSelect = { selected = it })
        } }
        onNodeWithText("iOS").performClick()
        assertEquals("ios", selected)
    }

    @Suppress("DEPRECATION")
    @Test
    fun platformFiltersAndCopyPathFitCompactWidth() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            Column(Modifier.width(328.dp)) {
                PlatformFilterRow(selected = "all", onSelect = {})
                StartFromRow(
                    screens = listOf("home_shelf", "book_detail", "search"),
                    selected = "home_shelf",
                    path = listOf("home_shelf"),
                    copyScope = "Demo project · Last 7 days",
                    onPick = {},
                )
            }
        } }
        listOf("All", "Android", "iOS", "Web", "Copy path").forEach { onNodeWithText(it).assertIsDisplayed() }
        val webRight = onNodeWithText("Web").fetchSemanticsNode().boundsInRoot.right
        val copyRight = onNodeWithText("Copy path").fetchSemanticsNode().boundsInRoot.right
        assert(webRight <= 360f) { "Flow Web filter extends past its 360dp viewport: $webRight" }
        assert(copyRight <= 360f) { "Flow Copy path action extends past its 360dp viewport: $copyRight" }
    }

    @Suppress("DEPRECATION")
    @Test
    fun headerMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    Column(modifier = Modifier.width(465.dp)) {
                        FlowHero()
                        PlatformFilterRow(selected = "all", onSelect = {}, modifier = Modifier.padding(top = 16.dp))
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

    @Suppress("DEPRECATION")
    @Test
    fun copyPathIncludesReadablePathAndSelectedScope() = runComposeUiTest {
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
        }
        setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                QuietMetrixTheme(ThemeMode.Dark) {
                    StartFromRow(
                        screens = listOf("home"),
                        selected = "home",
                        path = listOf("home", "search"),
                        copyScope = "Project: Demo · Range: Last 7 days · Platform: All",
                        onPick = {},
                    )
                }
            }
        }
        onNodeWithText("Copy path").performClick()
        assertEquals("Path: Home → Search\nProject: Demo · Range: Last 7 days · Platform: All", copied?.text)
    }

    // -- 3-card row (Task 23) -------------------------------------------------------------

    private val sizeClass = WindowSizeClass.Expanded

    // Platforms real (totalsByPlatform is a tracked counter dimension); countries/deviceClasses
    // populated here only to exercise the bar-list/progress-bar rendering path itself — see this
    // class's KDoc and `FlowPathLogic.kt`'s Task 23 section KDoc for why both are always `[]` in
    // every real deployment today.
    private val loadedCardsAgg = AggregatesResponse(
        platforms = listOf(BreakdownItem("android", 540), BreakdownItem("ios", 380), BreakdownItem("web", 80)),
        countries = listOf(BreakdownItem("Spain", 410), BreakdownItem("Mexico", 180), BreakdownItem("Argentina", 110)),
        deviceClasses = listOf(BreakdownItem("phone", 720), BreakdownItem("tablet", 150), BreakdownItem("desktop", 90)),
    )

    @Suppress("DEPRECATION")
    @Test
    fun cardsNormalStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreakdownCardsSection(
                        agg = loadedCardsAgg,
                        loading = false,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("flow/cards-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun cardsLoadingStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreakdownCardsSection(
                        agg = null,
                        loading = true,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("flow/cards-loading.png")
    }

    // The real, honest state for every field today: AggregatesResponse() defaults countries/
    // platforms/deviceClasses to emptyList(), so all 3 cards show EmptyState — not a hypothetical,
    // this is what a real server's /aggregates response renders as right now (see this class's
    // KDoc and FlowPathLogic.kt's Task 23 section KDoc).
    @Suppress("DEPRECATION")
    @Test
    fun cardsEmptyStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreakdownCardsSection(
                        agg = AggregatesResponse(),
                        loading = false,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("flow/cards-empty.png")
    }

    // Deliberately asserts against the SAME golden as cardsNormalStateMatchesGolden —
    // FlowBreakdownCardsSection has no server-reachability awareness of its own, matching
    // Overview's ScreensPlatformsCountriesSection/VitalsSection/DailyVolumeSection convention (the
    // screen-level InlineErrorNotice this task added for DataSection.Aggregates is what surfaces
    // the error; this row still renders whatever `agg` it was last given).
    @Suppress("DEPRECATION")
    @Test
    fun cardsErrorsStateMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreakdownCardsSection(
                        agg = loadedCardsAgg,
                        loading = false,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("flow/cards-normal.png")
    }

    @Suppress("DEPRECATION")
    @Test
    fun platformsCardShowsAtMostThreeStatTilesAndTheOsVersionsCaption() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreakdownCardsSection(
                        agg = loadedCardsAgg,
                        loading = false,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("android").assertExists()
        onNodeWithText("ios").assertExists()
        onNodeWithText("web").assertExists()
    }

    @Suppress("DEPRECATION")
    @Test
    fun deviceTypesCardShowsEmptyStateWhenDeviceClassesIsEmpty() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    FlowBreakdownCardsSection(
                        agg = AggregatesResponse(platforms = loadedCardsAgg.platforms),
                        loading = false,
                        sizeClass = sizeClass,
                        modifier = Modifier.width(960.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("phone").assertDoesNotExist()
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
        onNodeWithText("Search").performClick()
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
        onNodeWithText("Left the app").performClick()
        waitForIdle()
        assertEquals(null, picked)
    }
}
