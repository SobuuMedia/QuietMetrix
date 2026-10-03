package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.api.*
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.viewmodel.DashboardState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rendered detail proof; reference PNG is occluded by its drawer and has no usable detail crop. */
@OptIn(ExperimentalTestApi::class)
class ExperimentDetailInteractionTest {
    /** Declared variants retain historical allocation after rollout. */
    private val experiment = ExperimentDto("cta", "Checkout", status = "active", variants = listOf(VariantDto("a", 40), VariantDto("b", 60)))

    /** Real-shaped result comparison with a significant challenger. */
    private fun results(exp: ExperimentDto = experiment) = ExperimentResultsResponse(
        experiment = exp,
        variants = listOf(ExperimentVariantResultDto("a", 1000, 100, .1), ExperimentVariantResultDto("b", 1000, 200, .2)),
        comparison = ExperimentComparisonDto("a", "b", 5.0, .001, true, "b"),
    )

    /** A delayed previous-window winner cannot be shipped under the new window heading. */
    @Suppress("DEPRECATION")
    @Test
    fun staleRangeDoesNotOfferShip() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                ExperimentsBody(
                    DashboardState(user = ApiUser("admin", "admin@example.com"), range = TimeRange.Day7,
                        experiments = listOf(experiment), selectedExperimentKey = experiment.key,
                        experimentResultsRange = TimeRange.Day30, experimentResults = results().copy(range = ExperimentRangeDto("2026-09-01", "2026-10-01"))),
                    experiment, onSelect = {}, onAction = {},
                )
            }
        }
        onNodeWithText("Ship winner").assertDoesNotExist()
        onNodeWithText("Keep baseline").assertExists()
    }

    /** Raw statistics appear only when Expert is enabled; metrics and medal remain available normally. */
    @Suppress("DEPRECATION")
    @Test
    fun expertToggleControlsRawStatistics() = runComposeUiTest {
        setContent {
            var expert by remember { mutableStateOf(false) }
            QuietMetrixTheme(ThemeMode.Dark) {
                Column(Modifier.width(700.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { expert = !expert }) { Text("Toggle expert") }
                    ExperimentResultsPanel(DashboardState(expertMode = expert, experimentResultsRange = TimeRange.Default, experimentResults = results()), experiment, WindowSizeClass.Medium)
                }
            }
        }
        onNodeWithTag("experiment-winner-b").assertExists()
        onNodeWithTag("experiment-confidence-meter").assertExists()
        onNodeWithTag("experiment-raw-statistics").assertDoesNotExist()
        onNodeWithText("Toggle expert").performClick()
        onNodeWithTag("experiment-raw-statistics").assertExists()
        onNodeWithText("z = 5.0 · p = 0.001").assertExists()
    }

    /** Shipped medals use the persisted choice even when the live date-range comparison changes. */
    @Suppress("DEPRECATION")
    @Test
    fun shippedSelectionOverridesLiveWinner() = runComposeUiTest {
        val shipped = experiment.copy(status = "shipped", selectedVariant = "a")
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Column(Modifier.width(700.dp).verticalScroll(rememberScrollState())) {
                    ExperimentResultsPanel(DashboardState(experimentResultsRange = TimeRange.Default, experimentResults = results(shipped)), shipped, WindowSizeClass.Medium)
                }
            }
        }
        onNodeWithTag("experiment-winner-a").assertExists()
        onNodeWithTag("experiment-winner-b").assertDoesNotExist()
        onNodeWithText("Results remain live", substring = true).assertExists()
        onNodeWithText("b is winning in the selected range.").assertExists()
    }

    /** Cancel never mutates; only the shared dialog confirm button delivers the captured choice. */
    @Suppress("DEPRECATION")
    @Test
    fun endingActionRequiresConfirmation() = runComposeUiTest {
        var confirms = 0
        var dismisses = 0
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                ExperimentActionConfirmation(PendingExperimentAction(experiment, ExperimentAction.KeepBaseline, "a"),
                    onDismiss = { dismisses++ }, onConfirm = { confirms++ })
            }
        }
        assertEquals(0, confirms)
        onNodeWithText("Cancel").performClick()
        assertEquals(1, dismisses)
        assertEquals(0, confirms)
        onNodeWithText("Keep baseline", substring = false).performClick()
        assertEquals(1, confirms)
        onNodeWithText("subsequent app session", substring = true).assertExists()
    }
}
