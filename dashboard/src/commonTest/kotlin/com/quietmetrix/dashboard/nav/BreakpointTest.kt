package com.quietmetrix.dashboard.nav

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class BreakpointTest {

    @Test
    fun tinyWidthIsCompact() {
        assertEquals(WindowSizeClass.Compact, classifyWindow(0.dp))
        assertEquals(WindowSizeClass.Compact, classifyWindow(320.dp))
        assertEquals(WindowSizeClass.Compact, classifyWindow(598.dp))
    }

    @Test
    fun exactlyCompactMaxIsMedium() {
        // boundary: widthDp < compactMax -> Compact; 599 is not < 599 -> Medium
        assertEquals(WindowSizeClass.Medium, classifyWindow(599.dp))
    }

    @Test
    fun mediumRange() {
        assertEquals(WindowSizeClass.Medium, classifyWindow(600.dp))
        assertEquals(WindowSizeClass.Medium, classifyWindow(720.dp))
        assertEquals(WindowSizeClass.Medium, classifyWindow(838.dp))
    }

    @Test
    fun exactlyMediumMaxIsExpanded() {
        assertEquals(WindowSizeClass.Expanded, classifyWindow(839.dp))
    }

    @Test
    fun expandedRange() {
        assertEquals(WindowSizeClass.Expanded, classifyWindow(840.dp))
        assertEquals(WindowSizeClass.Expanded, classifyWindow(1024.dp))
        assertEquals(WindowSizeClass.Expanded, classifyWindow(1920.dp))
    }
}
