package com.quietmetrix.dashboard.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary       = Color(0xFF58A6FF),
    onPrimary     = Color(0xFF0D1117),
    background    = Color(0xFF0E1116),
    onBackground  = Color(0xFFE6EDF3),
    surface       = Color(0xFF161B22),
    onSurface     = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C2128),
    onSurfaceVariant = Color(0xFF8B949E),
    error         = Color(0xFFF85149),
    onError       = Color(0xFFFFFFFF),
)

@Composable
fun QuietMetrixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
