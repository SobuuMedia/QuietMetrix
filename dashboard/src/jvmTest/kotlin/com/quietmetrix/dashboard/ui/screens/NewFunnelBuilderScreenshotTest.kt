package com.quietmetrix.dashboard.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/** Screenshot regression for the actual dashboard funnel builder opened by the Funnels hero CTA. */
@OptIn(ExperimentalTestApi::class)
class NewFunnelBuilderScreenshotTest {
    @Suppress("DEPRECATION")
    @Test
    fun emptyBuilderMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                NewFunnelBuilderDialog(onDismiss = {}, onCreate = {})
            }
        }
        waitForIdle()
        onNode(isDialog()).captureToImage().assertMatchesGolden("funnels/new-builder-empty.png")
    }
}
