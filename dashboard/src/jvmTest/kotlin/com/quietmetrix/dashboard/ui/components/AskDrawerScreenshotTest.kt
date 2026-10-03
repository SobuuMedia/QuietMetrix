package com.quietmetrix.dashboard.ui.components

import androidx.compose.ui.test.*
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.testutil.writeAsGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import com.quietmetrix.dashboard.viewmodel.*
import kotlin.test.Test

/** Chrome regression after manual comparison with handoff screenshot 12; content uses real supported intents. */
@OptIn(ExperimentalTestApi::class)
class AskDrawerScreenshotTest {
    @Suppress("DEPRECATION")
    @Test
    fun answerWithSourceAndCopyMatchesGolden() = runComposeUiTest {
        val state = DashboardState(currentProjectId = "proj_1", projects = listOf(ApiProject("proj_1", "Sobuu")),
            askDrawer = AskDrawerState(open = true, messages = listOf(
                AskMessage(AskMessageRole.User, "How many sessions?"),
                AskMessage(AskMessageRole.Answer, "There were 120 sessions in the selected range.", "Sessions · Selected range: 7d"))))
        setContent { QuietMetrixTheme(ThemeMode.Dark) { AskDrawer(DashboardViewModel(), state) } }
        onNodeWithText("Copy numbers").assertExists()
        onNodeWithText("Sessions · Selected range: 7d").assertExists()
        val screenshot = onNodeWithTag("drawer-panel").captureToImage()
        if (System.getProperty("golden.record") == "true") screenshot.writeAsGolden("ask/answer.png")
        else screenshot.assertMatchesGolden("ask/answer.png")
    }
}
