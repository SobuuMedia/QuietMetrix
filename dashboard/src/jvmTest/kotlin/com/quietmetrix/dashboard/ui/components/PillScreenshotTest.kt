package com.quietmetrix.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quietmetrix.dashboard.testutil.assertMatchesGolden
import com.quietmetrix.dashboard.theme.QuietMetrixTheme
import com.quietmetrix.dashboard.theme.ThemeMode
import kotlin.test.Test

/**
 * Screenshot-golden coverage for [Pill]'s four variants, rendered together
 * in one row (dark theme, matching the nav row / vitals delta pill region of
 * `_source/01-overview-normal.png` that this golden was derived from — see
 * `goldens/README.md`'s log for the exact source crop this task used).
 */
@OptIn(ExperimentalTestApi::class)
class PillScreenshotTest {

    // See QuietMetrixThemeScreenshotTest's @Suppress comment (Task 1's
    // reviewed convention, commit ab300e0): v1 runComposeUiTest is what the
    // existing screenshot-golden infra is built on.
    @Suppress("DEPRECATION")
    @Test
    fun allVariantsMatchGolden() = runComposeUiTest {
        setContent {
            QuietMetrixTheme(ThemeMode.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill(text = "Overview", variant = PillVariant.NavActive)
                        Pill(text = "42", variant = PillVariant.Neutral)
                        Pill(text = "+18%", variant = PillVariant.Lime)
                        Pill(text = "Showing only iOS ✕", variant = PillVariant.Lavender)
                    }
                }
            }
        }
        waitForIdle()

        val bitmap = onRoot().captureToImage()

        bitmap.assertMatchesGolden("pill/variants.png")
    }
}
