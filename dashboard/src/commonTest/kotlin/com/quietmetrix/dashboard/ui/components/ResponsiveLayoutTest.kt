package com.quietmetrix.dashboard.ui.components

import com.quietmetrix.dashboard.nav.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsiveLayoutTest {

    @Test
    fun compactStacksIntoColumn() {
        assertEquals(LayoutFlow.StackedColumn, layoutFlowFor(WindowSizeClass.Compact))
    }

    @Test
    fun mediumLaysOutAsRow() {
        assertEquals(LayoutFlow.Row, layoutFlowFor(WindowSizeClass.Medium))
    }

    @Test
    fun expandedLaysOutAsRow() {
        assertEquals(LayoutFlow.Row, layoutFlowFor(WindowSizeClass.Expanded))
    }
}
