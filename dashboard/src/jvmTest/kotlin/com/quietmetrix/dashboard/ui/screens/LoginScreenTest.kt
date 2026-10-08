package com.quietmetrix.dashboard.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.login_error_invalid_credentials
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.testutil.writeAsGolden
import com.quietmetrix.dashboard.theme.DarkColors
import com.quietmetrix.dashboard.theme.LightColors
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@Suppress("DEPRECATION")
class LoginScreenTest {
    private fun ComposeUiTest.golden(path: String) {
        waitForIdle()
        val captured = onRoot().captureToImage()
        if (System.getProperty("golden.record") == "true") captured.writeAsGolden(path)
        captured.assertMatchesGolden(path)
    }

    @Test fun darkLoginAndSubmission() = runComposeUiTest {
        var credentials: Pair<String, String>? = null
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            LoginScreen(onLogin = { email, password -> credentials = email to password })
        } }
        onNodeWithText("QuietMetrix").assertIsDisplayed()
        onNode(hasText("Sign in") and hasClickAction()).assertIsNotEnabled()
        assertEquals(DarkColors.background, onRoot().captureToImage().toPixelMap()[0, 0])
        golden("login/dark.png")
        onNodeWithText("Email").performTextInput(" owner@example.com ")
        onNodeWithText("Password").performTextInput(" password ")
        onNode(hasText("Sign in") and hasClickAction()).performClick()
        assertEquals("owner@example.com" to " password ", credentials)
    }

    @Test fun lightLogin() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Light) { LoginScreen(onLogin = { _, _ -> }) } }
        assertEquals(LightColors.background, onRoot().captureToImage().toPixelMap()[0, 0])
        golden("login/light.png")
    }

    @Test fun compactErrorRemainsReachable() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            Box(Modifier.requiredSize(360.dp, 480.dp)) {
                LoginScreen(errorRes = Res.string.login_error_invalid_credentials, onLogin = { _, _ -> })
            }
        } }
        onNode(hasText("Sign in") and hasClickAction()).performScrollTo().assertIsDisplayed()
        onNodeWithText("Invalid email or password.").assertIsDisplayed()
        golden("login/compact-error.png")
    }

    @Test fun loadingDisablesInputsAndSubmission() = runComposeUiTest {
        setContent { QuietMetrixTheme(ThemeMode.Dark) {
            LoginScreen(loading = true, onLogin = { _, _ -> error("Must not submit while loading") })
        } }
        onNodeWithText("Email").assertIsNotEnabled()
        onNodeWithText("Password").assertIsNotEnabled()
        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    }
}
