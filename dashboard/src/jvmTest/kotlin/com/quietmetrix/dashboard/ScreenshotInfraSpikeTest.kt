package com.quietmetrix.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Throwaway spike proving the Compose Multiplatform UI-testing pipeline
 * (`runComposeUiTest` + `captureToImage()`) actually runs on the `jvm()`
 * target, before any real screenshot-regression infrastructure depends on
 * it. No golden comparison yet - just proof the bitmap comes back with real
 * pixel data of the expected size. Delete/replace in Task 2.
 */
class ScreenshotInfraSpikeTest {

    // Deliberately using the v1 runComposeUiTest here (the API the task
    // brief names); v1 is flagged deprecated in favor of a v2 API with
    // different default coroutine-dispatcher semantics
    // (StandardTestDispatcher vs. immediate execution). That v1-vs-v2
    // choice is a design decision for Task 2's real harness, not something
    // to silently swap here - suppressed only to keep this spike's output
    // clean.
    @Suppress("DEPRECATION")
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `captures a solid color box to a bitmap of the expected size`() = runComposeUiTest {
        setContent {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Red)
            )
        }

        val bitmap = onRoot().captureToImage()

        assertNotNull(bitmap)
        assertEquals(64, bitmap.width)
        assertEquals(64, bitmap.height)
    }
}
