package com.quietmetrix.dashboard.ui.components.charts

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartLogicTest {

    private fun approx(a: Float, b: Float, eps: Float = 0.01f) = abs(a - b) < eps

    // -- scaleLinear ---------------------------------------------------------

    @Test
    fun scaleLinearMapsEndpoints() {
        assertTrue(approx(0f, scaleLinear(0f, 100f, 0f, 200f, 0f)))
        assertTrue(approx(200f, scaleLinear(0f, 100f, 0f, 200f, 100f)))
        assertTrue(approx(100f, scaleLinear(0f, 100f, 0f, 200f, 50f)))
    }

    @Test
    fun scaleLinearHandlesOffsetRange() {
        // domain 0..100 -> range 10..110, midpoint 50 -> 60
        assertTrue(approx(60f, scaleLinear(0f, 100f, 10f, 110f, 50f)))
    }

    @Test
    fun scaleLinearDegenerateDomainReturnsRangeMidpoint() {
        assertTrue(approx(50f, scaleLinear(5f, 5f, 0f, 100f, 5f)))
    }

    // -- barRects ------------------------------------------------------------

    @Test
    fun barRectsEmptyInput() {
        assertTrue(barRects(emptyList(), 0f, 0f, 100f, 200f).isEmpty())
    }

    @Test
    fun barRectsCountMatchesValues() {
        val r = barRects(listOf(10f, 20f, 30f), 0f, 0f, 300f, 200f)
        assertEquals(3, r.size)
    }

    @Test
    fun barRectsHeightsRelativeToMax() {
        val r = barRects(listOf(0f, 50f, 100f), 0f, 0f, 300f, 200f, max = 100f)
        // areaHeight=200; max value bar reaches full height, zero bar has 0 height
        assertTrue(approx(200f, r[2].height), "max bar height = area height")
        assertTrue(approx(0f, r[0].height), "zero bar height = 0")
    }

    @Test
    fun barRectsBarWidthsFitArea() {
        val r = barRects(listOf(10f, 20f, 30f, 40f), 0f, 0f, 400f, 200f)
        val totalBarWidth = r.sumOf { it.width.toDouble() }
        assertTrue(totalBarWidth < 400.0, "bars + gaps must fit within area width")
        // all bars same width
        assertTrue(r.all { approx(it.width, r[0].width) })
    }

    @Test
    fun barRectsGrowUpwardFromBottom() {
        val r = barRects(listOf(100f), 0f, 0f, 100f, 200f, max = 100f)
        assertTrue(approx(0f, r[0].y), "full bar top touches area top")
    }

    // -- donutArcs -----------------------------------------------------------

    @Test
    fun donutArcsEmptyWhenNoTotal() {
        assertTrue(donutArcs(emptyList(), emptyList()).isEmpty())
        assertTrue(donutArcs(listOf(0f, 0f), listOf("a", "b")).isEmpty())
    }

    @Test
    fun donutArcsSweepsSumTo360() {
        val r = donutArcs(listOf(1f, 1f, 1f), listOf("a", "b", "c"))
        val total = r.sumOf { it.sweepAngle.toDouble() }
        assertTrue(abs(total - 360.0) < 0.01)
    }

    @Test
    fun donutArcsFractionsAndOrder() {
        val r = donutArcs(listOf(1f, 3f), listOf("a", "b"))
        assertTrue(approx(0.25f, r[0].fraction), "1/4")
        assertTrue(approx(0.75f, r[1].fraction), "3/4")
        assertTrue(approx(90f, r[0].sweepAngle), "0.25*360")
        assertTrue(approx(270f, r[1].sweepAngle), "0.75*360")
        // second slice starts where the first ended
        assertTrue(approx(r[0].startAngle + r[0].sweepAngle, r[1].startAngle))
    }

    // -- linePoints ----------------------------------------------------------

    @Test
    fun linePointsEmptyAndSingle() {
        assertTrue(linePoints(emptyList(), 0f, 0f, 100f, 200f).isEmpty())
        val one = linePoints(listOf(50f), 0f, 0f, 100f, 200f, max = 100f)
        assertEquals(1, one.size)
        assertTrue(approx(50f, one[0].x), "single point centered horizontally")
    }

    @Test
    fun linePointsSpanHorizontalRange() {
        val pts = linePoints(listOf(0f, 50f, 100f), 0f, 0f, 100f, 200f, max = 100f)
        assertEquals(3, pts.size)
        assertTrue(approx(0f, pts.first().x), "first x at area start")
        assertTrue(approx(100f, pts.last().x), "last x at area end")
    }

    @Test
    fun linePointsVerticalPositioning() {
        val pts = linePoints(listOf(0f, 100f), 0f, 0f, 100f, 200f, max = 100f)
        assertTrue(approx(200f, pts[0].y), "value 0 -> bottom")
        assertTrue(approx(0f, pts[1].y), "max value -> top")
    }

    // -- funnelBars ------------------------------------------------------------

    @Test
    fun funnelBarsEmptyInput() {
        assertTrue(funnelBars(emptyList(), 0f, 0f, 300f, 200f).isEmpty())
    }

    @Test
    fun funnelBarsSingleStepIsFullWidth() {
        val r = funnelBars(listOf(100), 0f, 0f, 300f, 100f)
        assertEquals(1, r.size)
        assertTrue(approx(300f, r[0].width), "the only step is 100% of entry")
        assertTrue(approx(1f, r[0].fraction))
    }

    @Test
    fun funnelBarsAllZeroDoesNotCrash() {
        val r = funnelBars(listOf(0, 0, 0), 0f, 0f, 300f, 200f)
        assertEquals(3, r.size)
        assertTrue(r.all { it.width == 0f && it.fraction == 0f })
    }

    @Test
    fun funnelBarsWidthsAreMonotonicallyNonIncreasing() {
        val r = funnelBars(listOf(100, 60, 60, 20), 0f, 0f, 400f, 200f)
        for (i in 1 until r.size) {
            assertTrue(r[i].width <= r[i - 1].width + 0.01f, "step ${i} must not be wider than step ${i - 1}")
        }
    }

    @Test
    fun funnelBarsFractionIsRelativeToEntryNotPreviousStep() {
        val r = funnelBars(listOf(100, 50, 25), 0f, 0f, 400f, 200f)
        assertTrue(approx(1f, r[0].fraction))
        assertTrue(approx(0.5f, r[1].fraction))
        assertTrue(approx(0.25f, r[2].fraction))
    }

    @Test
    fun funnelBarsAreHorizontallyCentered() {
        val r = funnelBars(listOf(100, 50), 0f, 0f, 400f, 200f)
        val entryCenter = r[0].x + r[0].width / 2f
        val secondCenter = r[1].x + r[1].width / 2f
        assertTrue(approx(entryCenter, secondCenter), "bars must share a horizontal center line")
        assertTrue(approx(200f, entryCenter), "centered within the 400px area")
    }

    @Test
    fun funnelBarsStackVerticallyWithinTheArea() {
        val r = funnelBars(listOf(100, 50, 25), 0f, 10f, 400f, 210f)
        assertTrue(approx(10f, r[0].y), "first bar starts at the area top")
        assertTrue(r[2].y + r[2].height <= 10f + 210f + 0.5f, "last bar stays within the area")
        // rows do not overlap
        assertTrue(r[1].y >= r[0].y + r[0].height - 0.5f)
        assertTrue(r[2].y >= r[1].y + r[1].height - 0.5f)
    }

    @Test
    fun funnelBarsHandlesAZeroEntryWithoutDivisionByZero() {
        val r = funnelBars(listOf(0, 5), 0f, 0f, 300f, 200f)
        assertEquals(2, r.size)
        assertTrue(r.all { it.fraction == 0f && it.width == 0f })
    }
}
