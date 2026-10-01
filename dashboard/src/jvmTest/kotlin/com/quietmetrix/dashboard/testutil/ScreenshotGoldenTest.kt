package com.quietmetrix.dashboard.testutil

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Self-test for [assertMatchesGolden] / [writeAsGolden] using tiny
 * hand-made 2x2 PNG fixtures checked in under
 * `src/jvmTest/resources/goldens/selftest/`: proves the comparator passes
 * on an identical bitmap, fails on a 1-pixel difference once the mismatch
 * percentage crosses the threshold, and respects both an overridden
 * threshold and a dimension mismatch.
 */
class ScreenshotGoldenTest {

    private val goldenResource = "selftest/golden.png"
    private val goldenFile = File("src/jvmTest/resources/goldens/$goldenResource")
    private val oneDiffPixelFile =
        File("src/jvmTest/resources/goldens/selftest/golden_1px_diff.png")

    private fun loadBitmap(file: File): ImageBitmap =
        ImageIO.read(file).toComposeImageBitmap()

    @Test
    fun `identical bitmap matches its golden at the default threshold`() {
        val captured = loadBitmap(goldenFile)

        // Should not throw.
        captured.assertMatchesGolden(goldenResource)
    }

    @Test
    fun `one mismatched pixel out of four exceeds the default 2 percent threshold`() {
        val captured = loadBitmap(oneDiffPixelFile)

        val error = assertFailsWith<AssertionError> {
            captured.assertMatchesGolden(goldenResource)
        }
        assertTrue(
            error.message.orEmpty().contains(goldenResource),
            "Expected failure message to name the golden path, got: ${error.message}",
        )
    }

    @Test
    fun `the same one-pixel mismatch passes once the threshold is raised above it`() {
        val captured = loadBitmap(oneDiffPixelFile)

        // 1 of 4 pixels = 25% mismatch; a 50% threshold must tolerate it.
        captured.assertMatchesGolden(goldenResource, thresholdPct = 50.0)
    }

    @Test
    fun `a size mismatch against the golden fails without attempting to resize`() {
        val differentlySized = ImageBitmap(width = 3, height = 3)

        assertFailsWith<AssertionError> {
            differentlySized.assertMatchesGolden(goldenResource, thresholdPct = 99.0)
        }
    }

    @Test
    fun `writeAsGolden refuses to run without the golden record Gradle property`() {
        val captured = loadBitmap(goldenFile)
        val originalProperty = System.getProperty("golden.record")
        System.clearProperty("golden.record")
        try {
            assertFailsWith<IllegalStateException> {
                captured.writeAsGolden("selftest/should_not_be_written.png")
            }
        } finally {
            if (originalProperty != null) {
                System.setProperty("golden.record", originalProperty)
            }
        }
        assertTrue(
            !File("src/jvmTest/resources/goldens/selftest/should_not_be_written.png").exists(),
            "writeAsGolden must not write a file when the record flag is unset",
        )
    }

    @Test
    fun `a missing golden file fails with a clear message instead of a stack trace`() {
        val captured = loadBitmap(goldenFile)

        val error = assertFailsWith<IllegalStateException> {
            captured.assertMatchesGolden("selftest/does_not_exist.png")
        }
        assertTrue(error.message.orEmpty().contains("does_not_exist.png"))
    }
}
