package com.quietmetrix.dashboard.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.bricolage_grotesque_bold
import com.quietmetrix.dashboard.resources.bricolage_grotesque_extrabold
import com.quietmetrix.dashboard.resources.bricolage_grotesque_medium
import com.quietmetrix.dashboard.resources.bricolage_grotesque_regular
import com.quietmetrix.dashboard.resources.bricolage_grotesque_semibold
import com.quietmetrix.dashboard.resources.jetbrains_mono_bold
import com.quietmetrix.dashboard.resources.jetbrains_mono_medium
import com.quietmetrix.dashboard.resources.jetbrains_mono_regular
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.Font

/**
 * Verifies [QmTypography] and [QmTypographyExtras] (theme/Typography.kt)
 * actually resolve to the bundled Bricolage Grotesque / JetBrains Mono font
 * files rather than a platform default, and that the sizes/weights picked
 * for each Material3 slot match the ranges chosen from
 * design_handoff_dashboard_redesign/README.md > Design System > Typography.
 *
 * This lives in jvmTest, not the shared commonTest `ThemeTokensTest`,
 * because reading [QmTypography] requires loading a bundled font through
 * Compose Multiplatform's resource system, which is only possible inside a
 * composition. `runComposeUiTest` (backed by the `compose.uiTest` /
 * `compose.desktop.currentOs` jvmTest dependencies already used for
 * screenshot-golden coverage, see ScreenshotGoldenTest) is the dashboard
 * module's existing way to run one. On the JVM target, Compose
 * Multiplatform's resource loader resolves bundled fonts synchronously
 * (`ResourceState.blocking.kt`), so no extra wait beyond `waitForIdle()` is
 * needed for the real font bytes to be loaded.
 */
@OptIn(ExperimentalTestApi::class)
class TypographyFontTest {

    /** Rebuilds the bundled Bricolage Grotesque family exactly as Typography.kt does, for equality checks. */
    @Composable
    private fun expectedBricolageGrotesque(): FontListFontFamily = FontFamily(
        Font(Res.font.bricolage_grotesque_regular, FontWeight.Normal),
        Font(Res.font.bricolage_grotesque_medium, FontWeight.Medium),
        Font(Res.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
        Font(Res.font.bricolage_grotesque_bold, FontWeight.Bold),
        Font(Res.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
    ) as FontListFontFamily

    /** Rebuilds the bundled JetBrains Mono family exactly as Typography.kt does, for equality checks. */
    @Composable
    private fun expectedJetBrainsMono(): FontListFontFamily = FontFamily(
        Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
    ) as FontListFontFamily

    @Test
    fun headingSlotsResolveToBundledBricolageGrotesque() = runComposeUiTest {
        lateinit var expected: FontListFontFamily
        var display: FontFamily? = null
        var headline: FontFamily? = null
        var title: FontFamily? = null
        var body: FontFamily? = null
        var label: FontFamily? = null
        setContent {
            expected = expectedBricolageGrotesque()
            display = QmTypography.displayLarge.fontFamily
            headline = QmTypography.headlineLarge.fontFamily
            title = QmTypography.titleLarge.fontFamily
            body = QmTypography.bodyMedium.fontFamily
            label = QmTypography.labelSmall.fontFamily
        }
        waitForIdle()

        assertEquals(expected, display, "displayLarge must use the bundled Bricolage Grotesque family")
        assertEquals(expected, headline, "headlineLarge must use the bundled Bricolage Grotesque family")
        assertEquals(expected, title, "titleLarge must use the bundled Bricolage Grotesque family")
        assertEquals(expected, body, "bodyMedium must use the bundled Bricolage Grotesque family")
        assertEquals(expected, label, "labelSmall must use the bundled Bricolage Grotesque family")
        assertNotEquals(FontFamily.Default, display, "must not be left on the system default font")
    }

    @Test
    fun extrasMonoStylesResolveToBundledJetBrainsMono() = runComposeUiTest {
        lateinit var expected: FontListFontFamily
        var metricMono: FontFamily? = null
        var metricMonoEmphasis: FontFamily? = null
        setContent {
            expected = expectedJetBrainsMono()
            metricMono = QmTypographyExtras.metricMono.fontFamily
            metricMonoEmphasis = QmTypographyExtras.metricMonoEmphasis.fontFamily
        }
        waitForIdle()

        assertEquals(expected, metricMono, "metricMono must use the bundled JetBrains Mono family")
        assertEquals(
            expected,
            metricMonoEmphasis,
            "metricMonoEmphasis must use the bundled JetBrains Mono family",
        )
    }

    /**
     * Carries forward the pre-Task-6 `typographyScaleIsOrdered` assertion
     * (formerly in the shared commonTest `ThemeTokensTest`, which can no
     * longer read `QmTypography` outside a composition): display > headline
     * > body > label must still hold now that every slot also carries a
     * bundled [FontFamily].
     */
    @Test
    fun typographyScaleIsOrdered() = runComposeUiTest {
        var display: TextStyle? = null
        var headline: TextStyle? = null
        var body: TextStyle? = null
        var label: TextStyle? = null
        setContent {
            display = QmTypography.displayLarge
            headline = QmTypography.headlineLarge
            body = QmTypography.bodyMedium
            label = QmTypography.labelSmall
        }
        waitForIdle()

        val displaySize = checkNotNull(display).fontSize
        val headlineSize = checkNotNull(headline).fontSize
        val bodySize = checkNotNull(body).fontSize
        val labelSize = checkNotNull(label).fontSize

        assertTrue(displaySize > headlineSize, "display > headline")
        assertTrue(headlineSize > bodySize, "headline > body")
        assertTrue(bodySize > labelSize, "body > label")
    }

    @Test
    fun noMaterial3SlotUsesJetBrainsMono() = runComposeUiTest {
        lateinit var mono: FontListFontFamily
        val slots = mutableListOf<TextStyle>()
        setContent {
            mono = expectedJetBrainsMono()
            slots += listOf(
                QmTypography.displayLarge, QmTypography.displayMedium, QmTypography.displaySmall,
                QmTypography.headlineLarge, QmTypography.headlineMedium, QmTypography.headlineSmall,
                QmTypography.titleLarge, QmTypography.titleMedium, QmTypography.titleSmall,
                QmTypography.bodyLarge, QmTypography.bodyMedium, QmTypography.bodySmall,
                QmTypography.labelLarge, QmTypography.labelMedium, QmTypography.labelSmall,
            )
        }
        waitForIdle()

        slots.forEach { style ->
            assertNotEquals(mono, style.fontFamily, "no Material3 slot should resolve to JetBrains Mono")
        }
    }

    @Test
    fun heroCardValueAndSectionTitleSizesMatchChosenSpecValues() = runComposeUiTest {
        var hero: TextStyle? = null
        var cardValue: TextStyle? = null
        var sectionTitle: TextStyle? = null
        setContent {
            hero = QmTypography.displayLarge
            cardValue = QmTypography.displayMedium
            sectionTitle = QmTypography.headlineLarge
        }
        waitForIdle()

        val heroStyle = checkNotNull(hero)
        val cardValueStyle = checkNotNull(cardValue)
        val sectionTitleStyle = checkNotNull(sectionTitle)

        // README: "hero H1 46–52px/700 tracking -0.025em" — 48sp chosen.
        assertEquals(48.sp, heroStyle.fontSize, "hero H1 size")
        assertEquals(FontWeight.Bold, heroStyle.fontWeight, "hero H1 weight")
        assertEquals((-0.025).em, heroStyle.letterSpacing, "hero H1 tracking")

        // README: "card values 34–44px/700" — 40sp chosen.
        assertEquals(40.sp, cardValueStyle.fontSize, "card value size")
        assertEquals(FontWeight.Bold, cardValueStyle.fontWeight, "card value weight")

        // README: "section titles 20–22px/700" — 22sp chosen.
        assertEquals(22.sp, sectionTitleStyle.fontSize, "section title size")
        assertEquals(FontWeight.Bold, sectionTitleStyle.fontWeight, "section title weight")
    }

    @Test
    fun explicitFontWeightIsSetOnEveryBundledFontEntry() = runComposeUiTest {
        lateinit var bricolage: FontListFontFamily
        lateinit var mono: FontListFontFamily
        setContent {
            bricolage = expectedBricolageGrotesque()
            mono = expectedJetBrainsMono()
        }
        waitForIdle()

        val expectedBricolageWeights = setOf(
            FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
        )
        assertEquals(5, bricolage.fonts.size)
        assertEquals(expectedBricolageWeights, bricolage.fonts.map { it.weight }.toSet())

        val expectedMonoWeights = setOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)
        assertEquals(3, mono.fonts.size)
        assertEquals(expectedMonoWeights, mono.fonts.map { it.weight }.toSet())
    }
}
