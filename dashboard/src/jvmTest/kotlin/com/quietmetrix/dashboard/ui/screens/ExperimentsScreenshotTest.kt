package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.ApiUser
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.api.ExperimentResultsResponse
import com.quietmetrix.dashboard.api.ExperimentVariantResultDto
import com.quietmetrix.dashboard.api.ExperimentDto
import com.quietmetrix.dashboard.api.VariantDto
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Screenshot-golden coverage for Task 30's Experiments card list (`ExperimentsScreen.kt`):
 * the new `ExperimentCardList`/`ExperimentCard` composables that replace the old
 * `ExperimentPicker` dropdown.
 *
 * **Golden derivation** (`experiments/card-list-*.png`): real-crop derivation checked and
 * REJECTED — `_source/08-experiments-normal.png`'s only visible Experiments content is the
 * header nav + the "🧪 Experiments NEW" eyebrow and the 3-line H1 ("Show two versions of a
 * screen piece, and let your users pick the winner."); the card-list/detail region is outside the
 * usable frame: only a clipped sliver of a list card appears at the bottom, beneath the
 * "MOCKUP ONLY" toolbar, while the "Ask your data" drawer covers the right pane — the same rejection
 * already documented for `funnels/switcher-*.png` and `funnels/cards-*.png` (Task 28) and
 * `retention/cards-normal.png` (Task 24). Derived instead directly from the real composable
 * render with fixture `ExperimentDto`s covering all 4 real statuses (active/draft/paused/ended),
 * `QuietMetrixTheme(Dark)`, `Modifier.width(320.dp)` (the sidebar width used in `ExperimentsTab`),
 * default 1024x768 `runComposeUiTest` canvas, recorded with `-Pgolden.record=true`.
 */
@OptIn(ExperimentalTestApi::class)
class ExperimentsScreenshotTest {

    /** Creates a real-shaped experiment fixture for card-list rendering. */
    private fun experiment(
        key: String,
        name: String,
        status: String,
        screen: String? = null,
        trafficPercent: Int = 100,
    ) = ExperimentDto(
        key = key,
        name = name,
        screen = screen,
        status = status,
        trafficPercent = trafficPercent,
        variants = listOf(VariantDto("a", 50), VariantDto("b", 50)),
    )

    /** Covers every supported backend status without inventing terminal outcomes. */
    private val fourStatuses = listOf(
        experiment("checkout_cta", "Checkout CTA color", status = "active", screen = "checkout", trafficPercent = 100),
        experiment("signup_hero", "Signup hero copy", status = "draft", trafficPercent = 50),
        experiment("paywall_price", "Paywall price framing", status = "paused", screen = "paywall", trafficPercent = 20),
        experiment("onboarding_flow", "Onboarding flow length", status = "ended", trafficPercent = 100),
    )

    /** Verifies the recorded dark card-list rendering. */
    @Suppress("DEPRECATION")
    @Test
    fun cardListNormalMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ExperimentCardList(
                        experiments = fourStatuses,
                        selectedKey = "checkout_cta",
                        onSelect = {},
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        onRoot().captureToImage().assertMatchesGolden("experiments/card-list-normal.png")
    }

    /** Verifies localized badges reflect each supported backend status. */
    @Suppress("DEPRECATION")
    @Test
    fun cardListShowsOneStatusBadgePerCardUsingTheFourWayMapping() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ExperimentCardList(
                        experiments = fourStatuses,
                        selectedKey = "checkout_cta",
                        onSelect = {},
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        // active -> Running, draft -> Draft, paused -> Paused, ended -> Finished (never a
        // fabricated Shipped/Closed split — see ExperimentCardStatus's KDoc).
        onNodeWithText("Running").assertExists()
        onNodeWithText("Draft").assertExists()
        onNodeWithText("Paused").assertExists()
        onNodeWithText("Finished").assertExists()
        onAllNodesWithText("Shipped").assertCountEquals(0)
        onAllNodesWithText("Closed").assertCountEquals(0)
    }

    /** Verifies card clicks deliver the selected experiment key. */
    @Suppress("DEPRECATION")
    @Test
    fun clickingACardSelectsItByKey() = runComposeUiTest {
        var selected: String? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ExperimentCardList(
                        experiments = fourStatuses,
                        selectedKey = "checkout_cta",
                        onSelect = { selected = it },
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Paywall price framing").performClick()
        waitForIdle()
        assertEquals("paywall_price", selected)
    }

    /** Verifies screen metadata and correctly formatted traffic percentages. */
    @Suppress("DEPRECATION")
    @Test
    fun cardMetaLineShowsScreenAndTrafficWhenScreenIsSet() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    ExperimentCardList(
                        experiments = fourStatuses,
                        selectedKey = "checkout_cta",
                        onSelect = {},
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Screen: checkout · Traffic: 100%").assertExists()
        // No screen set on signup_hero -> meta line falls back to traffic only.
        onNodeWithText("Traffic: 50%").assertExists()
    }
    /** Verifies constrained desktop/tablet content stacks and every action remains usable. */
    @Suppress("DEPRECATION")
    @Test
    fun narrowBodyStacksAndKeepsActionsInsideItsBounds() = runComposeUiTest {
        val fixture = fourStatuses.first()
        val actions = mutableListOf<ExperimentAction>()
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(Modifier.width(560.dp)) {
                    ExperimentsBody(
                        state = DashboardState(user = ApiUser("admin", "admin@example.com"), experiments = listOf(fixture), selectedExperimentKey = fixture.key),
                        experiment = fixture,
                        onSelect = {},
                        onAction = { actions += it },
                    )
                }
            }
        }
        waitForIdle()
        val card = onNodeWithText(fixture.name).fetchSemanticsNode().boundsInRoot
        val detail = onNodeWithText("Active").fetchSemanticsNode().boundsInRoot
        assertTrue(detail.top >= card.bottom, "Detail must stack below the list at a constrained tablet width")
        for (label in listOf("Pause", "End")) {
            val node = onNodeWithText(label)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width > 0f && bounds.left >= 0f && bounds.right <= 560f, "$label must remain inside the body")
            node.performClick()
        }
        assertEquals(listOf(ExperimentAction.Pause, ExperimentAction.End), actions)
    }

    /** Verifies compact details wrap draft actions and stack result KPIs by actual pane width. */
    @Suppress("DEPRECATION")
    @Test
    fun compactDraftKeepsDeleteUsableAndStacksResultCards() = runComposeUiTest {
        val fixture = fourStatuses[1]
        val actions = mutableListOf<ExperimentAction>()
        val results = ExperimentResultsResponse(
            experiment = fixture,
            variants = listOf(ExperimentVariantResultDto("Result A", rate = 0.2), ExperimentVariantResultDto("Result B", rate = 0.3)),
        )
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(Modifier.width(360.dp)) {
                    ExperimentsBody(
                        state = DashboardState(user = ApiUser("admin", "admin@example.com"), experiments = listOf(fixture), selectedExperimentKey = fixture.key, experimentResults = results),
                        experiment = fixture,
                        onSelect = {},
                        onAction = { actions += it },
                    )
                }
            }
        }
        waitForIdle()
        for (label in listOf("Start", "Delete")) {
            val node = onNodeWithText(label)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width > 0f && bounds.left >= 0f && bounds.right <= 360f, "$label must remain inside the body")
            node.performClick()
        }
        assertEquals(listOf(ExperimentAction.Start, ExperimentAction.Delete), actions)
        val first = onAllNodesWithText("Result A")[0].fetchSemanticsNode().boundsInRoot
        val second = onAllNodesWithText("Result B")[0].fetchSemanticsNode().boundsInRoot
        assertTrue(second.top >= first.bottom, "Result cards must stack inside a compact detail pane")
    }

}
