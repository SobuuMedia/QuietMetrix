package com.quietmetrix.dashboard.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.api.AggregatesResponse
import com.quietmetrix.dashboard.api.Totals
import com.quietmetrix.dashboard.nav.WindowSizeClass
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/** A successfully fetched event count is not a session count; failed fields stay unavailable. */
@OptIn(ExperimentalTestApi::class)
class OverviewUnavailableTest {
    @Suppress("DEPRECATION")
    @Test
    fun missingSessionsAreNotRenderedAsEventCountOrZeroDuration() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                VitalsSection(AggregatesResponse(totals = Totals(events = 100)), null, null, false, WindowSizeClass.Expanded)
            }
        }
        onAllNodesWithText("—").assertCountEquals(4)
        onNodeWithText("100").assertDoesNotExist()
        onNodeWithText("0s").assertDoesNotExist()
    }

    @Suppress("DEPRECATION")
    @Test
    fun suppressedPeopleExplainsThePrivacyThreshold() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                VitalsSection(
                    AggregatesResponse(peopleState = "suppressed"),
                    null, null, false, WindowSizeClass.Expanded,
                )
            }
        }
        onNodeWithText("Hidden because fewer than five installations contributed to this group.").assertExists()
    }
}
