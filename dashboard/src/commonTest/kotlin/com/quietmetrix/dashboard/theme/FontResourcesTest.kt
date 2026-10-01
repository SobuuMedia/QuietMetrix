package com.quietmetrix.dashboard.theme

import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.bricolage_grotesque_bold
import com.quietmetrix.dashboard.resources.bricolage_grotesque_extrabold
import com.quietmetrix.dashboard.resources.bricolage_grotesque_medium
import com.quietmetrix.dashboard.resources.bricolage_grotesque_regular
import com.quietmetrix.dashboard.resources.bricolage_grotesque_semibold
import com.quietmetrix.dashboard.resources.jetbrains_mono_bold
import com.quietmetrix.dashboard.resources.jetbrains_mono_medium
import com.quietmetrix.dashboard.resources.jetbrains_mono_regular
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task 4 gate — guarantees the bundled Bricolage Grotesque and JetBrains Mono
 * static TTF files exist under `composeResources/font/` and that
 * compose-resources codegen has generated a `Res.font.*` accessor for each
 * one. Also verifies, at runtime, that each accessor actually resolves to
 * real TrueType font bytes (not an empty or placeholder file) by reading the
 * resource via [getFontResourceBytes] and checking both a minimum byte count
 * and the TTF/OTF binary signature.
 *
 * Task 6 (not this task) is responsible for wiring these into FontFamily
 * objects in Typography.kt.
 */
class FontResourcesTest {

    /**
     * Every `Res.font.*` accessor this task bundles, paired with the family
     * name used only for failure messages.
     */
    private val expectedFonts: List<Pair<String, FontResource>> = listOf(
        "Bricolage Grotesque Regular" to Res.font.bricolage_grotesque_regular,
        "Bricolage Grotesque Medium" to Res.font.bricolage_grotesque_medium,
        "Bricolage Grotesque SemiBold" to Res.font.bricolage_grotesque_semibold,
        "Bricolage Grotesque Bold" to Res.font.bricolage_grotesque_bold,
        "Bricolage Grotesque ExtraBold" to Res.font.bricolage_grotesque_extrabold,
        "JetBrains Mono Regular" to Res.font.jetbrains_mono_regular,
        "JetBrains Mono Medium" to Res.font.jetbrains_mono_medium,
        "JetBrains Mono Bold" to Res.font.jetbrains_mono_bold,
    )

    @Test
    fun allExpectedFontAccessorsExist() {
        // Merely referencing every accessor above is already a compile-time
        // proof that compose-resources codegen generated it. This assertion
        // just confirms the list wasn't accidentally left empty.
        assertTrue(expectedFonts.size == 8, "expected exactly 8 bundled font accessors")
    }

    @Test
    fun everyFontResourceResolvesToRealTtfBytes() = runTest {
        val environment = getSystemResourceEnvironment()
        for ((label, resource) in expectedFonts) {
            val bytes = getFontResourceBytes(environment, resource)
            assertTrue(
                bytes.size > 10_000,
                "$label: expected a real TTF (tens of KB), got ${bytes.size} bytes",
            )
            assertTrue(
                isTtfOrOtfSignature(bytes),
                "$label: first 4 bytes ${bytes.take(4)} do not match a TTF/OTF signature",
            )
        }
    }

    /**
     * Checks the leading 4-byte magic number against the signatures used by
     * TrueType (`0x00010000` or `true`) and OpenType-with-CFF (`OTTO`) font
     * files, per the sfnt header spec.
     */
    private fun isTtfOrOtfSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val sfntVersion = bytes.copyOfRange(0, 4)
        val trueTypeSignature = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        val otherTrueTypeSignature = "true".encodeToByteArray()
        val openTypeCffSignature = "OTTO".encodeToByteArray()
        return sfntVersion.contentEquals(trueTypeSignature) ||
            sfntVersion.contentEquals(otherTrueTypeSignature) ||
            sfntVersion.contentEquals(openTypeCffSignature)
    }
}
