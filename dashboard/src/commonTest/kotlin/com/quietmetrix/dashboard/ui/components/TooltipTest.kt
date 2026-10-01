package com.quietmetrix.dashboard.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-logic coverage for [tooltipPlacement], the above/below flip decision behind [Tooltip]
 * (dashboard-redesign README > Interactions & Behavior > "Tooltips": "every ⓘ icon shows a
 * fixed-position tooltip on hover ... positioned above by default, flips below if too close to
 * the top of the viewport"). No screenshot golden is provided for this task (per the task-13
 * brief), so this logic test plus a manual visual check against the HTML prototype's tooltip
 * pattern is the gate.
 */
class TooltipTest {

    @Test
    fun anchorNearTopFlipsBelow() {
        // Anchor at y=10 with a 40-tall tooltip and the default 8 gap needs 48px of headroom;
        // only 10 is available, so it must flip below rather than clip off the top of the screen.
        val placement = tooltipPlacement(anchorY = 10f, tooltipHeight = 40f)
        assertEquals(TooltipPlacement.Below, placement)
    }

    @Test
    fun anchorComfortablyBelowThresholdStaysAbove() {
        // Plenty of headroom (200px) above a 40-tall tooltip: default placement holds.
        val placement = tooltipPlacement(anchorY = 200f, tooltipHeight = 40f)
        assertEquals(TooltipPlacement.Above, placement)
    }

    @Test
    fun exactBoundaryFitsAboveInclusive() {
        // anchorY - gap - tooltipHeight == 0 exactly: the tooltip's top edge lands precisely on
        // the viewport's top edge, which still counts as "fits" (no clipping), so it stays above.
        val placement = tooltipPlacement(anchorY = 48f, tooltipHeight = 40f, gap = 8f)
        assertEquals(TooltipPlacement.Above, placement)
    }

    @Test
    fun oneBelowBoundaryFlipsBelow() {
        // Same geometry as the boundary case but anchored 1px higher: the tooltip would now clip
        // 1px off the top, so it must flip.
        val placement = tooltipPlacement(anchorY = 47f, tooltipHeight = 40f, gap = 8f)
        assertEquals(TooltipPlacement.Below, placement)
    }

    @Test
    fun anchorAtViewportTopFlipsBelow() {
        val placement = tooltipPlacement(anchorY = 0f, tooltipHeight = 40f)
        assertEquals(TooltipPlacement.Below, placement)
    }

    @Test
    fun tallerTooltipNeedsMoreHeadroomToStayAbove() {
        // Same anchorY that was comfortably "Above" for a 40-tall tooltip (anchorComfortablyBelowThresholdStaysAbove)
        // flips below once the tooltip itself grows tall enough to no longer fit in that headroom.
        val placement = tooltipPlacement(anchorY = 200f, tooltipHeight = 500f)
        assertEquals(TooltipPlacement.Below, placement)
    }

    @Test
    fun customGapIsRespected() {
        // With a larger explicit gap, the same anchor/tooltip pair that fit above with the
        // default gap no longer has enough headroom and flips below.
        val placement = tooltipPlacement(anchorY = 48f, tooltipHeight = 40f, gap = 20f)
        assertEquals(TooltipPlacement.Below, placement)
    }
}
