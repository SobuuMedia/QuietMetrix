package com.quietmetrix.analytics.internal

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Unit tests for [ScreenTracker.enrichWithDwell] — the helper that stamps
 * running dwell (time-on-current-screen) onto every event's props. These tests
 * exercise the pure ScreenTracker logic directly and do NOT call [trackEvent],
 * which requires platform host context (Android Application context) that is
 * unavailable in the host-test compilation.
 */
@OptIn(ExperimentalTime::class)
class DwellEnrichmentTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000_000)

    @BeforeTest
    fun setUp() = runTest {
        ScreenTracker.reset()
    }

    @AfterTest
    fun tearDown() = runTest {
        ScreenTracker.reset()
    }

    @Test
    fun `stamps running dwell when a screen is active`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        val enriched = ScreenTracker.enrichWithDwell(emptyMap(), now = t0.plus(2.seconds))
        assertEquals(mapOf("duration_ms" to 2000L), enriched)
    }

    @Test
    fun `preserves other props when stamping`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        val enriched = ScreenTracker.enrichWithDwell(mapOf("tab" to "feed"), now = t0.plus(1.seconds))
        assertEquals(mapOf("tab" to "feed", "duration_ms" to 1000L), enriched)
    }

    @Test
    fun `returns unchanged when no screen is active`() = runTest {
        val enriched = ScreenTracker.enrichWithDwell(mapOf("x" to 1), now = t0)
        assertEquals(mapOf("x" to 1), enriched)
    }

    @Test
    fun `does not overwrite an existing duration_ms`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        val enriched = ScreenTracker.enrichWithDwell(mapOf("duration_ms" to 999L), now = t0.plus(2.seconds))
        assertEquals(mapOf("duration_ms" to 999L), enriched)
    }

    @Test
    fun `does not stamp zero dwell`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        val enriched = ScreenTracker.enrichWithDwell(emptyMap(), now = t0)
        assertTrue(enriched.isEmpty())
    }

    @Test
    fun `does not stamp negative dwell`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        val enriched = ScreenTracker.enrichWithDwell(emptyMap(), now = t0.minus(1.seconds))
        assertTrue(enriched.isEmpty())
    }

    @Test
    fun `currentDwellMs returns null when no screen is active`() = runTest {
        assertEquals(null, ScreenTracker.currentDwellMs(now = t0))
    }

    @Test
    fun `currentDwellMs returns elapsed milliseconds when a screen is active`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        assertEquals(2500L, ScreenTracker.currentDwellMs(now = t0.plus(2.5.seconds)))
    }
}
