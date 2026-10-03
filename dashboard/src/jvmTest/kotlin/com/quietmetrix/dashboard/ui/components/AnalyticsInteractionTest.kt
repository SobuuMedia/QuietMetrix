package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.ui.components.charts.BarChartCanvas
import com.quietmetrix.dashboard.ui.components.charts.BarData
import com.quietmetrix.dashboard.viewmodel.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers real click targets, including the taller prior series and Ask prefill semantics. */
@OptIn(ExperimentalTestApi::class)
class AnalyticsInteractionTest {
    @Suppress("DEPRECATION")
    @Test
    fun currentBarCanBeClickedAfterComparisonChangesItsScale() = runComposeUiTest {
        var selected: Int? = null
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(Modifier.width(400.dp)) {
                    BarChartCanvas(listOf(BarData("Oct 3", 10f)), modifier = Modifier.testTag("chart"),
                        previousValues = listOf(100f), onBarClick = { selected = it })
                }
            }
        }
        onNodeWithTag("chart").performTouchInput { click(Offset(width * .4f, height * .87f)) }
        assertEquals(0, selected)
    }

    @Suppress("DEPRECATION")
    @Test
    fun suggestionPrefillsWithoutSendingAnUnrequestedQuestion() = runComposeUiTest {
        val viewModel = DashboardViewModel()
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                AskDrawer(viewModel, DashboardState(askDrawer = AskDrawerState(open = true)))
            }
        }
        onNodeWithText("How many sessions?").performClick()
        assertEquals("How many sessions?", viewModel.state.value.askDrawer.draft)
        assertEquals(emptyList(), viewModel.state.value.askDrawer.messages)
    }
}
