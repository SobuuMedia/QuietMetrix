package com.quietmetrix.dashboard.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeTokensTest {

    @Test
    fun darkColorsArePopulated() {
        assertTrue(DarkColors.primary != Color.Unspecified, "primary must be set")
        assertTrue(DarkColors.background != Color.Unspecified, "background must be set")
        assertTrue(DarkColors.surface != Color.Unspecified, "surface must be set")
        assertTrue(DarkColors.error != Color.Unspecified, "error must be set")
    }

    @Test
    fun lightColorsArePopulated() {
        assertTrue(LightColors.primary != Color.Unspecified, "primary must be set")
        assertTrue(LightColors.background != Color.Unspecified, "background must be set")
        assertTrue(LightColors.surface != Color.Unspecified, "surface must be set")
        assertTrue(LightColors.error != Color.Unspecified, "error must be set")
    }

    @Test
    fun darkAndLightDiffer() {
        assertFalse(DarkColors.background == LightColors.background, "themes must not be identical")
        assertFalse(DarkColors.primary == LightColors.primary, "primary must differ between themes")
    }

    @Test
    fun extendedColorsHaveAtLeastFourSeries() {
        assertTrue(DarkExtendedColors.chartSeries.size >= 4, "need >=4 chart colors for breakdowns")
        assertTrue(LightExtendedColors.chartSeries.size >= 4, "need >=4 chart colors for breakdowns")
    }

    // -----------------------------------------------------------------------
    // Spec palette (design_handoff_dashboard_redesign/README.md > Design
    // System > Colors) — exact-value assertions. These pin the literal
    // hex/rgba values every downstream task (typography, shapes, theme
    // wiring, and every screen) will rely on via MaterialTheme.colorScheme
    // and LocalExtendedColors.current.
    // -----------------------------------------------------------------------

    @Test
    fun darkColorsMatchSpecPalette() {
        assertEquals(Color(0xFF131017), DarkColors.background, "--bg dark")
        assertEquals(Color(0xFFF5F0E8), DarkColors.onBackground, "--ink dark (onBackground)")
        assertEquals(Color(0xFF1C1822), DarkColors.surface, "--sf dark")
        assertEquals(Color(0xFFF5F0E8), DarkColors.onSurface, "--ink dark (onSurface)")
        assertEquals(Color(0xFF2A2531), DarkColors.surfaceVariant, "--sf2 dark")
        assertEquals(Color(0xFFB3ABBD), DarkColors.onSurfaceVariant, "--ink2 dark")
    }

    @Test
    fun lightColorsMatchSpecPalette() {
        assertEquals(Color(0xFFF7F2EA), LightColors.background, "--bg light")
        assertEquals(Color(0xFF1B1720), LightColors.onBackground, "--ink light (onBackground)")
        assertEquals(Color(0xFFFFFFFF), LightColors.surface, "--sf light")
        assertEquals(Color(0xFF1B1720), LightColors.onSurface, "--ink light (onSurface)")
        assertEquals(Color(0xFFEFE7DA), LightColors.surfaceVariant, "--sf2 light")
        assertEquals(Color(0xFF5A5265), LightColors.onSurfaceVariant, "--ink2 light")
    }

    @Test
    fun darkExtendedColorsMatchSpecPalette() {
        assertEquals(Color.White.copy(alpha = 0.08f), DarkExtendedColors.hairline, "--ln dark")
        assertEquals(Color.White.copy(alpha = 0.09f), DarkExtendedColors.chipFill, "--chip dark")
        assertEquals(
            Color(0xFFC8F25A).copy(alpha = 0.28f),
            DarkExtendedColors.barHighlight,
            "--hl dark",
        )
    }

    @Test
    fun lightExtendedColorsMatchSpecPalette() {
        assertEquals(
            Color(0xFF281932).copy(alpha = 0.11f),
            LightExtendedColors.hairline,
            "--ln light",
        )
        assertEquals(
            Color(0xFF281932).copy(alpha = 0.07f),
            LightExtendedColors.chipFill,
            "--chip light",
        )
        assertEquals(
            Color(0xFF96C828).copy(alpha = 0.30f),
            LightExtendedColors.barHighlight,
            "--hl light",
        )
    }

    @Test
    fun fixedAccentsDoNotThemeSwap() {
        assertEquals(Color(0xFFC8F25A), DarkExtendedColors.lime, "lime dark")
        assertEquals(Color(0xFFC8F25A), LightExtendedColors.lime, "lime light")
        assertEquals(DarkExtendedColors.lime, LightExtendedColors.lime, "lime must not theme-swap")

        assertEquals(Color(0xFF16131A), DarkExtendedColors.onLime, "onLime dark")
        assertEquals(Color(0xFF16131A), LightExtendedColors.onLime, "onLime light")

        assertEquals(Color(0xFFB9A7FF), DarkExtendedColors.lavender, "lavender dark")
        assertEquals(Color(0xFFB9A7FF), LightExtendedColors.lavender, "lavender light")
        assertEquals(
            DarkExtendedColors.lavender,
            LightExtendedColors.lavender,
            "lavender must not theme-swap",
        )

        assertEquals(Color(0xFFFF8A5B), DarkExtendedColors.coral, "coral dark")
        assertEquals(Color(0xFFFF8A5B), LightExtendedColors.coral, "coral light")
        assertEquals(DarkExtendedColors.coral, LightExtendedColors.coral, "coral must not theme-swap")
    }

    // -----------------------------------------------------------------------
    // Task 6 wired QmTypography to the bundled Bricolage Grotesque / JetBrains
    // Mono fonts (theme/Typography.kt). Reading QmTypography now requires
    // loading a bundled font via Compose Multiplatform's resource system,
    // which only works inside a composition — not possible from a plain
    // kotlin.test function in this shared commonTest. The scale-ordering
    // assertion that used to live here, plus the new font-family/size
    // assertions, moved to TypographyFontTest (dashboard/src/jvmTest),
    // which drives a real composition via runComposeUiTest.
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Spec shape scale (design_handoff_dashboard_redesign/README.md > Design
    // System > Spacing & shape, reconciled with the Design Tokens summary) —
    // card radius 20px lives on Material3's extraLarge slot; pill/chip/modal/
    // drawer have no Material3 Shapes slot of their own, so they live on the
    // supplementary QmShapeTokens holder. Unlike Shapes.kt, this is plain
    // data (RoundedCornerShape), so it needs no composition to construct and
    // genuinely belongs in commonTest.
    // -----------------------------------------------------------------------

    @Test
    fun cardRadiusMatchesSpec() {
        assertEquals(RoundedCornerShape(20.dp), QmShapes.extraLarge, "card radius (Spacing & shape: 20px)")
    }

    @Test
    fun shapeTokensMatchSpecPalette() {
        assertEquals(RoundedCornerShape(999.dp), QmShapeTokens.pill, "pill radius (999px full pill)")
        assertEquals(
            RoundedCornerShape(10.dp),
            QmShapeTokens.chip,
            "chip radius (9-12px per Spacing & shape, narrowed to 9-10px per Design Tokens; 10dp fits both)",
        )
        assertEquals(RoundedCornerShape(20.dp), QmShapeTokens.card, "card radius (20px), aliasing QmShapes.extraLarge")
        assertEquals(RoundedCornerShape(24.dp), QmShapeTokens.modal, "modal radius (24px)")
        assertEquals(RoundedCornerShape(20.dp), QmShapeTokens.drawer, "drawer radius (20px, coincides with card)")
    }
}
