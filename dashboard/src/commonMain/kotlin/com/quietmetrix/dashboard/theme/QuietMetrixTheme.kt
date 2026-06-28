package com.quietmetrix.dashboard.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { Light, Dark, System }

/** Currently selected (saved) theme mode — Light/Dark/System. */
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.Dark }

/** Changes the saved mode and persists it. Provided by the app root. */
val LocalThemeModeSetter = compositionLocalOf<(ThemeMode) -> Unit> {
    error("LocalThemeModeSetter not provided")
}

@Composable
fun QuietMetrixTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit,
) {
    val effective = resolveThemeMode(themeMode)
    val colors = if (effective == ThemeMode.Light) LightColors else DarkColors
    val extended = if (effective == ThemeMode.Light) LightExtendedColors else DarkExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colors,
            typography = QmTypography,
            shapes = QmShapes,
            content = content,
        )
    }
}

@Composable
@ReadOnlyComposable
private fun resolveThemeMode(mode: ThemeMode): ThemeMode {
    // System mode is resolved by the host (wasmJs reads prefers-color-scheme)
    // and passed in as Light/Dark before reaching the theme. When System is
    // passed directly we fall back to Dark to stay deterministic in tests.
    return if (mode == ThemeMode.System) ThemeMode.Dark else mode
}
