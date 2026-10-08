package com.quietmetrix.dashboard.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.api.AiCapabilities
import com.quietmetrix.dashboard.api.FunnelStepResultDto
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.ui.screens.FunnelStepList
import com.quietmetrix.dashboard.viewmodel.AskDrawerState
import com.quietmetrix.dashboard.viewmodel.DashboardState
import com.quietmetrix.dashboard.viewmodel.DashboardViewModel
import kotlin.test.Test

/** Ask stays discoverable and explains when the optional service is unavailable. */
@OptIn(ExperimentalTestApi::class)
class AiAccessTest {
    @Test fun `unconfigured drawer explains that analytics still work`() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) { AskDrawer(DashboardViewModel(),DashboardState(askDrawer=AskDrawerState(open=true))) } }
        onNodeWithText("Ask your data").assertExists()
        onNodeWithText("Ask is not configured for this server yet. Your dashboard analytics remain available without AI answers.").assertExists()
    }
    @Test fun `exhausted drawer disables submissions`() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) { AskDrawer(DashboardViewModel(),DashboardState(ai=AiCapabilities(true,false,"ai_allowance_exhausted","2026-11-01"),askDrawer=AskDrawerState(open=true,draft="sessions"))) } }
        onNodeWithText("Send").assertIsNotEnabled()
        onNodeWithText("How many sessions?").assertDoesNotExist()
        onNodeWithText("The project has used its AI allowance for this month. It resets on 2026-11-01.").assertExists()
    }
    @Test fun `funnel Ask hides without entitlement`() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) { CompositionLocalProvider(LocalAiAccess provides AiCapabilities()) {
            FunnelStepList(listOf(FunnelStepResultDto(key="one",count=20),FunnelStepResultDto(key="two",count=10,dropped=10,dropRate=.5)))
        } } }
        onNodeWithText("Why did they leave?").assertDoesNotExist()
    }
    @Test fun `funnel Ask disables after allowance exhaustion`() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) { CompositionLocalProvider(LocalAiAccess provides AiCapabilities(true,false)) {
            FunnelStepList(listOf(FunnelStepResultDto(key="one",count=20),FunnelStepResultDto(key="two",count=10,dropped=10,dropRate=.5)))
        } } }
        onNodeWithText("Why did they leave?").assertIsNotEnabled()
    }
}
