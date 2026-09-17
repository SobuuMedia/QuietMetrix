package com.quietmetrix.analytics.internal.counters

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RageTapDetector] is pure logic over `(x, y, timestampMs)` samples — no platform UI code —
 * so the detection heuristic (3+ taps within 1.5s, each within a small radius of the previous
 * one) is fully unit-testable without a real Android/iOS touch event.
 */
class RageTapDetectorTest {

    private fun detector() = RageTapDetector(maxTaps = 3, windowMs = 1_500L, radiusPx = 24f)

    @Test
    fun `three taps in the same spot within the window fire on the third`() {
        val d = detector()
        assertFalse(d.onTap(100f, 100f, 0L))
        assertFalse(d.onTap(101f, 101f, 200L))
        assertTrue(d.onTap(100f, 102f, 400L))
    }

    @Test
    fun `two taps never fire`() {
        val d = detector()
        assertFalse(d.onTap(100f, 100f, 0L))
        assertFalse(d.onTap(101f, 101f, 200L))
    }

    @Test
    fun `taps spread across more than the window do not fire`() {
        val d = detector()
        assertFalse(d.onTap(100f, 100f, 0L))
        assertFalse(d.onTap(100f, 100f, 800L))
        // Third tap is 1600ms after the first -- outside the 1500ms window.
        assertFalse(d.onTap(100f, 100f, 1_600L))
    }

    @Test
    fun `a tap far from the previous one resets the burst instead of extending it`() {
        val d = detector()
        assertFalse(d.onTap(100f, 100f, 0L))
        assertFalse(d.onTap(101f, 101f, 200L))
        // Far away (500px) -- starts a fresh burst rather than completing this one.
        assertFalse(d.onTap(600f, 600f, 400L))
        assertFalse(d.onTap(601f, 601f, 600L))
        assertTrue(d.onTap(600f, 602f, 800L))
    }

    @Test
    fun `a tap exactly at the radius boundary still extends the burst`() {
        val d = detector()
        assertFalse(d.onTap(0f, 0f, 0L))
        // Exactly 24px away.
        assertFalse(d.onTap(24f, 0f, 100L))
        assertTrue(d.onTap(24f, 24f, 200L))
    }

    @Test
    fun `after firing, a fresh burst can fire again`() {
        val d = detector()
        d.onTap(0f, 0f, 0L)
        d.onTap(0f, 0f, 100L)
        assertTrue(d.onTap(0f, 0f, 200L))

        assertFalse(d.onTap(0f, 0f, 300L))
        assertFalse(d.onTap(0f, 0f, 400L))
        assertTrue(d.onTap(0f, 0f, 500L))
    }

    @Test
    fun `normal, well-spaced taps never fire`() {
        val d = detector()
        for (i in 0 until 10) {
            assertFalse(d.onTap(i * 100f, 0f, i * 2_000L))
        }
    }
}
