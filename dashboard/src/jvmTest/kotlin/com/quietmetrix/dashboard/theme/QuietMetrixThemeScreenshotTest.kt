package com.quietmetrix.dashboard.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import kotlin.test.Test

/**
 * First real (non-spike) use of the screenshot-testing pipeline built in
 * Tasks 1-2: renders a full [QuietMetrixTheme] composition — not a bare
 * Compose primitive — in both [ThemeMode.Dark] and [ThemeMode.Light], fills
 * the canvas with `MaterialTheme.colorScheme.background`, captures it, and
 * compares against a golden. This proves the theme actually resolves to the
 * dashboard-redesign spec's background colors (dark `#131017`, light
 * `#f7f2ea`, see theme/Color.kt's `QmDarkBg`/`QmLightBg`) when consumed the
 * way real screens will consume it: through `QuietMetrixTheme { ... }`,
 * reading `MaterialTheme.colorScheme` rather than the token objects
 * directly.
 */
@OptIn(ExperimentalTestApi::class)
class QuietMetrixThemeScreenshotTest {

    @Test
    fun darkThemeBackgroundMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("_infra/theme-dark-bg.png")
    }

    @Test
    fun lightThemeBackgroundMatchesGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Light) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("_infra/theme-light-bg.png")
    }
}
