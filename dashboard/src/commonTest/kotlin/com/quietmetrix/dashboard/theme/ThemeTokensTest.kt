package com.quietmetrix.dashboard.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
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

    @Test
    fun typographyScaleIsOrdered() {
        val display = QmTypography.displayLarge.fontSize
        val headline = QmTypography.headlineLarge.fontSize
        val body = QmTypography.bodyMedium.fontSize
        val label = QmTypography.labelSmall.fontSize
        assertTrue(display > headline, "display > headline")
        assertTrue(headline > body, "headline > body")
        assertTrue(body > label, "body > label")
    }
}
