package com.quietmetrix.dashboard.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Dark palette (GitHub dark — preserves the previous look)
// ---------------------------------------------------------------------------

internal val QmPrimary = Color(0xFF58A6FF)
internal val QmPrimaryContainer = Color(0xFF1F6FEB)
internal val QmOnPrimary = Color(0xFF0D1117)

internal val QmBackground = Color(0xFF0E1116)
internal val QmOnBackground = Color(0xFFE6EDF3)
internal val QmSurface = Color(0xFF161B22)
internal val QmOnSurface = Color(0xFFE6EDF3)
internal val QmSurfaceVariant = Color(0xFF1C2128)
internal val QmOnSurfaceVariant = Color(0xFF8B949E)
internal val QmOutline = Color(0xFF30363D)

internal val QmError = Color(0xFFF85149)
internal val QmOnError = Color(0xFFFFFFFF)
internal val QmErrorContainer = Color(0xFF4D2424)
internal val QmOnErrorContainer = Color(0xFFFFB1B0)

internal val QmSecondary = Color(0xFF8B949E)
internal val QmOnSecondary = Color(0xFF0D1117)
internal val QmSecondaryContainer = Color(0xFF2A2F37)
internal val QmOnSecondaryContainer = Color(0xFFC9D1D9)

val DarkColors: ColorScheme = darkColorScheme(
    primary = QmPrimary,
    onPrimary = QmOnPrimary,
    primaryContainer = QmPrimaryContainer,
    onPrimaryContainer = Color(0xFFE6EDF3),
    secondary = QmSecondary,
    onSecondary = QmOnSecondary,
    secondaryContainer = QmSecondaryContainer,
    onSecondaryContainer = QmOnSecondaryContainer,
    background = QmBackground,
    onBackground = QmOnBackground,
    surface = QmSurface,
    onSurface = QmOnSurface,
    surfaceVariant = QmSurfaceVariant,
    onSurfaceVariant = QmOnSurfaceVariant,
    outline = QmOutline,
    error = QmError,
    onError = QmOnError,
    errorContainer = QmErrorContainer,
    onErrorContainer = QmOnErrorContainer,
)

// ---------------------------------------------------------------------------
// Light palette (GitHub light)
// ---------------------------------------------------------------------------

internal val QmLightPrimary = Color(0xFF0969DA)
internal val QmLightOnPrimary = Color(0xFFFFFFFF)
internal val QmLightPrimaryContainer = Color(0xFFDDF4FF)
internal val QmLightOnPrimaryContainer = Color(0xFF001A41)

internal val QmLightBackground = Color(0xFFFFFFFF)
internal val QmLightOnBackground = Color(0xFF1F2328)
internal val QmLightSurface = Color(0xFFF6F8FA)
internal val QmLightOnSurface = Color(0xFF1F2328)
internal val QmLightSurfaceVariant = Color(0xFFE8EAED)
internal val QmLightOnSurfaceVariant = Color(0xFF656D76)
internal val QmLightOutline = Color(0xFFD0D7DE)

internal val QmLightError = Color(0xFFCF222E)
internal val QmLightOnError = Color(0xFFFFFFFF)
internal val QmLightErrorContainer = Color(0xFFFFE6E6)
internal val QmLightOnErrorContainer = Color(0xFF4D0001)

internal val QmLightSecondary = Color(0xFF656D76)
internal val QmLightOnSecondary = Color(0xFFFFFFFF)
internal val QmLightSecondaryContainer = Color(0xFFE8EAED)
internal val QmLightOnSecondaryContainer = Color(0xFF1F2328)

val LightColors: ColorScheme = lightColorScheme(
    primary = QmLightPrimary,
    onPrimary = QmLightOnPrimary,
    primaryContainer = QmLightPrimaryContainer,
    onPrimaryContainer = QmLightOnPrimaryContainer,
    secondary = QmLightSecondary,
    onSecondary = QmLightOnSecondary,
    secondaryContainer = QmLightSecondaryContainer,
    onSecondaryContainer = QmLightOnSecondaryContainer,
    background = QmLightBackground,
    onBackground = QmLightOnBackground,
    surface = QmLightSurface,
    onSurface = QmLightOnSurface,
    surfaceVariant = QmLightSurfaceVariant,
    onSurfaceVariant = QmLightOnSurfaceVariant,
    outline = QmLightOutline,
    error = QmLightError,
    onError = QmLightOnError,
    errorContainer = QmLightErrorContainer,
    onErrorContainer = QmLightOnErrorContainer,
)

// ---------------------------------------------------------------------------
// Extended brand/chart tokens (not part of Material3 ColorScheme)
// ---------------------------------------------------------------------------

@Immutable
data class ExtendedColors(
    val demoBadge: Color,
    val onDemoBadge: Color,
    val chartAxis: Color,
    val chartGrid: Color,
    val chartSeries: List<Color>,
    /** Drop-off bar/wedge in the funnel chart — deliberately not part of [chartSeries] so a
     * project's series colors never accidentally collide with the "someone left here" signal. */
    val chartDropoff: Color,
)

val DarkExtendedColors = ExtendedColors(
    demoBadge = Color(0xFFD29922),
    onDemoBadge = Color(0xFF1C2128),
    chartAxis = Color(0xFF30363D),
    chartGrid = Color(0xFF21262D),
    chartSeries = listOf(
        Color(0xFF58A6FF),
        Color(0xFF3FB950),
        Color(0xFFD29922),
        Color(0xFFF778BA),
        Color(0xFFA371F7),
        Color(0xFFF85149),
    ),
    chartDropoff = Color(0xFFF85149),
)

val LightExtendedColors = ExtendedColors(
    demoBadge = Color(0xFFBF8700),
    onDemoBadge = Color(0xFFFFFFFF),
    chartAxis = Color(0xFFD0D7DE),
    chartGrid = Color(0xFFE8EAED),
    chartSeries = listOf(
        Color(0xFF0969DA),
        Color(0xFF1A7F37),
        Color(0xFFBF8700),
        Color(0xFF8250DF),
        Color(0xFFDB61A4),
        Color(0xFFCF222E),
    ),
    chartDropoff = Color(0xFFCF222E),
)

val LocalExtendedColors = staticCompositionLocalOf<ExtendedColors> {
    error("ExtendedColors not provided. Wrap the tree in QuietMetrixTheme.")
}
