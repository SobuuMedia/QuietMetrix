package com.quietmetrix.dashboard.nav

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowSizeClass { Compact, Medium, Expanded }

object Breakpoints {
    val compactMax: Dp = 599.dp
    val mediumMax: Dp = 839.dp
}

/**
 * Maps a viewport width (in dp) to a Material-style window size class.
 *  - Compact: phones (below 600dp) -> side drawer + hamburger
 *  - Medium:  small tablets (600-839dp) -> permanent navigation rail
 *  - Expanded: tablets/desktop (840dp+) -> permanent navigation rail
 *
 * Pure function so it can be unit tested without a Compose host.
 */
fun classifyWindow(widthDp: Dp): WindowSizeClass = when {
    widthDp < Breakpoints.compactMax -> WindowSizeClass.Compact
    widthDp < Breakpoints.mediumMax -> WindowSizeClass.Medium
    else -> WindowSizeClass.Expanded
}
