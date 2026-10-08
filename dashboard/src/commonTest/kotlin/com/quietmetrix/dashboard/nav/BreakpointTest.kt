package com.quietmetrix.dashboard.nav

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class BreakpointTest {

    @Test
    fun tinyWidthIsCompact() {
        assertEquals(WindowSizeClass.Compact, classifyWindow(0.dp))
        assertEquals(WindowSizeClass.Compact, classifyWindow(320.dp))
        assertEquals(WindowSizeClass.Compact, classifyWindow(599.dp))
    }

    @Test
    fun exactlyCompactMaxIsMedium() {
        assertEquals(WindowSizeClass.Medium, classifyWindow(600.dp))
    }

    @Test
    fun mediumRange() {
        assertEquals(WindowSizeClass.Medium, classifyWindow(601.dp))
        assertEquals(WindowSizeClass.Medium, classifyWindow(768.dp))
        assertEquals(WindowSizeClass.Medium, classifyWindow(1023.dp))
    }

    @Test
    fun exactlyMediumMaxIsExpanded() {
        assertEquals(WindowSizeClass.Expanded, classifyWindow(1024.dp))
    }

    @Test
    fun expandedRange() {
        assertEquals(WindowSizeClass.Expanded, classifyWindow(1025.dp))
        assertEquals(WindowSizeClass.Expanded, classifyWindow(1280.dp))
        assertEquals(WindowSizeClass.Expanded, classifyWindow(1920.dp))
    }
}
