package com.quietmetrix.dashboard.ui.screens

import androidx.compose.ui.test.*
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
        onAllNodesWithText("—").assertCountEquals(3)
        onNodeWithText("100").assertDoesNotExist()
        onNodeWithText("0s").assertDoesNotExist()
    }
}
