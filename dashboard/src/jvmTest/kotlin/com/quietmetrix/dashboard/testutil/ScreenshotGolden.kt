package com.quietmetrix.dashboard.testutil

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.fail

/**
 * Root directory, relative to the `dashboard` module, holding golden PNG
 * fixtures that [assertMatchesGolden] compares captured Composable
 * screenshots against. Resolved as a plain relative [File] (not a
 * classpath resource) so [writeAsGolden] can write back into the checked-in
 * source tree rather than a build-output copy; this relies on Gradle's
 * `Test` task running with its working directory at the module root, which
 * is the default and is not overridden in this build.
 */
private val GOLDENS_DIR = File("src/jvmTest/resources/goldens")

/**
 * Directory where a failing [assertMatchesGolden] call writes the actual
 * rendering it captured, so a failed screenshot test leaves behind a PNG a
 * human can open and compare against the golden by eye.
 */
private val DIFF_OUTPUT_DIR = File("build/test-results/goldens")

/**
 * Per-channel (0-255) tolerance below which two pixels' red, green and blue
 * values still count as matching. This absorbs trivial rounding noise
 * between a freshly-rendered Skia capture and a PNG re-decoded from disk
 * without letting any visibly different pixel slip through as a match.
 */
private const val CHANNEL_TOLERANCE = 2

/**
 * Compares this captured [ImageBitmap] against the PNG golden stored at
 * `src/jvmTest/resources/goldens/<path>`, failing the test (via
 * [kotlin.test.fail]) if the percentage of mismatched pixels — RGB only,
 * alpha is ignored — exceeds [thresholdPct].
 *
 * Dimension mismatch policy: if this bitmap's width or height differs from
 * the golden's, no resize is attempted on either side. The comparison is
 * treated as a 100% mismatch and fails immediately. Resizing one image to
 * fit the other would silently hide exactly the kind of regression (a
 * broken layout, a dropped component, a changed breakpoint) this
 * comparator exists to catch, so a size difference is always a failure
 * rather than something to normalize away.
 *
 * On failure, the actual bitmap is written as a PNG diff artifact under
 * `build/test-results/goldens/` (mirroring [path], with an `-actual.png`
 * suffix) for human inspection; the failure message names that file.
 */
fun ImageBitmap.assertMatchesGolden(path: String, thresholdPct: Double = 2.0) {
    val goldenFile = File(GOLDENS_DIR, path)
    check(goldenFile.exists()) {
        "Golden file not found at ${goldenFile.path}. " +
            "Run with -Pgolden.record=true to (re)generate it."
    }
    val golden = ImageIO.read(goldenFile)
    val actual = this.toAwtImage()

    val mismatchPct = mismatchPercentage(actual, golden)
    if (mismatchPct > thresholdPct) {
        val diffFile = writeDiffArtifact(path, actual)
        fail(
            "Screenshot mismatch for '$path': %.2f%% of pixels differ (threshold %.2f%%). ".format(
                mismatchPct,
                thresholdPct,
            ) + "Actual rendering written to ${diffFile.path} for inspection.",
        )
    }
}

/**
 * Writes this [ImageBitmap] as the golden PNG at
 * `src/jvmTest/resources/goldens/<path>`, overwriting whatever is already
 * there. Gated behind the `-Pgolden.record=true` Gradle property (surfaced
 * to the JVM as the `golden.record` system property) so goldens are only
 * ever (re)generated as an explicit, opt-in action and never as a side
 * effect of an ordinary test run.
 */
fun ImageBitmap.writeAsGolden(path: String) {
    check(System.getProperty("golden.record") == "true") {
        "writeAsGolden(\"$path\") requires -Pgolden.record=true; refusing to " +
            "overwrite a golden during a normal test run."
    }
    val file = File(GOLDENS_DIR, path)
    file.parentFile?.mkdirs()
    ImageIO.write(this.toAwtImage(), "png", file)
}

/**
 * Fraction of pixels, expressed as a 0-100 percentage, whose RGB channels
 * differ by more than [CHANNEL_TOLERANCE] between [actual] and [golden].
 * Returns 100.0 without comparing a single pixel if the two images' sizes
 * differ (see [assertMatchesGolden]'s dimension mismatch policy).
 */
private fun mismatchPercentage(actual: BufferedImage, golden: BufferedImage): Double {
    if (actual.width != golden.width || actual.height != golden.height) return 100.0

    var mismatched = 0
    for (y in 0 until actual.height) {
        for (x in 0 until actual.width) {
            if (!pixelsMatch(actual.getRGB(x, y), golden.getRGB(x, y))) mismatched++
        }
    }
    val total = actual.width * actual.height
    return mismatched * 100.0 / total
}

/**
 * True if two ARGB-packed pixels' red, green and blue channels are each
 * within [CHANNEL_TOLERANCE] of one another. Alpha is deliberately ignored:
 * goldens and captures are both opaque UI renderings, and comparing alpha
 * would only make the comparator more sensitive to incidental
 * transparency-handling differences, not visual regressions.
 */
private fun pixelsMatch(a: Int, b: Int): Boolean {
    val ar = (a shr 16) and 0xFF
    val ag = (a shr 8) and 0xFF
    val ab = a and 0xFF
    val br = (b shr 16) and 0xFF
    val bg = (b shr 8) and 0xFF
    val bb = b and 0xFF
    return abs(ar - br) <= CHANNEL_TOLERANCE &&
        abs(ag - bg) <= CHANNEL_TOLERANCE &&
        abs(ab - bb) <= CHANNEL_TOLERANCE
}

/**
 * Saves [actual] as a PNG under `build/test-results/goldens/`, mirroring
 * the golden's relative [path] with an `-actual.png` suffix, and returns
 * that file so the caller can report its location.
 */
private fun writeDiffArtifact(path: String, actual: BufferedImage): File {
    val diffPath = path.substringBeforeLast('.', path) + "-actual.png"
    val diffFile = File(DIFF_OUTPUT_DIR, diffPath)
    diffFile.parentFile?.mkdirs()
    ImageIO.write(actual, "png", diffFile)
    return diffFile
}
