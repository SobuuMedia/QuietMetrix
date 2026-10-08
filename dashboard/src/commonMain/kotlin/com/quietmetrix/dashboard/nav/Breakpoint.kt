package com.quietmetrix.dashboard.nav

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowSizeClass { Compact, Medium, Expanded }

object Breakpoints {
    val compactMax: Dp = 600.dp
    val mediumMax: Dp = 1024.dp
}

/**
 * Maps a viewport width (in dp) to a Material-style window size class.
 *  - Compact: phones (below 600dp)
 *  - Medium:  tablets and narrow desktop (600-1023dp)
 *  - Expanded: desktop/tablets (1024dp+)
 *
 * Pure function so it can be unit tested without a Compose host.
 */
fun classifyWindow(widthDp: Dp): WindowSizeClass = when {
    widthDp < Breakpoints.compactMax -> WindowSizeClass.Compact
    widthDp < Breakpoints.mediumMax -> WindowSizeClass.Medium
    else -> WindowSizeClass.Expanded
}
