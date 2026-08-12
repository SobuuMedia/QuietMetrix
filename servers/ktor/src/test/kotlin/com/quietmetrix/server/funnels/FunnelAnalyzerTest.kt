package com.quietmetrix.server.funnels

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunnelAnalyzerTest {

    private fun t(seconds: Long) = Instant.fromEpochSeconds(seconds)

    private fun row(
        actor: String, event: String, seconds: Long,
        screen: String? = null, platform: String? = null,
    ) = FunnelActorRow(actorKey = actor, eventName = event, ts = t(seconds), screen = screen, platform = platform)

    private val twoSteps = listOf(
        FunnelStepDefinition(key = "view", event = "screen_view", screen = "signup"),
        FunnelStepDefinition(key = "submit", event = "signup_submitted"),
    )

    @Test
    fun `basic entered, converted and per-step conversion`() {
        val rows = listOf(
            row("A", "screen_view", 0, "signup"),
            row("A", "signup_submitted", 10),
            row("B", "screen_view", 0, "signup"),
            // B never submits
        )
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000))

        assertEquals(2, result.entered)
        assertEquals(1, result.converted)
        assertEquals(0.5, result.overallConversion)
        assertEquals(2, result.steps[0].count)
        assertEquals(1.0, result.steps[0].conversionFromEntry)
        assertEquals(0, result.steps[0].dropped)
        assertEquals(1, result.steps[1].count)
        assertEquals(0.5, result.steps[1].conversionFromEntry)
        assertEquals(0.5, result.steps[1].conversionFromPrevious)
        assertEquals(1, result.steps[1].dropped)
        assertEquals(0.5, result.steps[1].dropRate)
    }

    @Test
    fun `an actor with no matching events at all is not counted`() {
        val rows = listOf(row("A", "screen_view", 0, "signup"))
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000))
        assertEquals(1, result.entered)
    }

    @Test
    fun `an entry outside the requested range is excluded entirely`() {
        // The SQL layer widens the query range to catch late completions, so this row set can
        // contain an actor whose ENTRY predates [from, to) — they must not be counted at all,
        // not even at step 1, even though their step-1 event technically matches.
        val rows = listOf(
            row("early", "screen_view", 0, "signup"),   // entry before `from`
            row("early", "signup_submitted", 50),
            row("in-range", "screen_view", 100, "signup"),
            row("in-range", "signup_submitted", 110),
        )
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(90), to = t(1000))

        assertEquals(1, result.entered)
        assertEquals(1, result.converted)
    }

    @Test
    fun `median and p90 time to convert across multiple actors`() {
        val rows = listOf(
            row("A", "screen_view", 0, "signup"), row("A", "signup_submitted", 10),
            row("B", "screen_view", 0, "signup"), row("B", "signup_submitted", 20),
            row("C", "screen_view", 0, "signup"), row("C", "signup_submitted", 30),
        )
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000))

        // Deltas in ms: 10000, 20000, 30000 — nearest-rank median (n=3) is the middle value.
        assertEquals(20_000L, result.medianTotalMs)
        assertEquals(20_000L, result.steps[1].medianMsFromPrevious)
    }

    @Test
    fun `step 0 never has a time-from-previous`() {
        val rows = listOf(row("A", "screen_view", 0, "signup"), row("A", "signup_submitted", 10))
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000))
        assertNull(result.steps[0].medianMsFromPrevious)
        assertNull(result.steps[0].p90MsFromPrevious)
    }

    @Test
    fun `breakdown groups actors by their entry event's dimension value`() {
        val rows = listOf(
            row("A", "screen_view", 0, "signup", platform = "android"),
            row("A", "signup_submitted", 10),
            row("B", "screen_view", 0, "signup", platform = "ios"),
            // B does not submit
            row("C", "screen_view", 0, "signup", platform = "android"),
            row("C", "signup_submitted", 10),
        )
        val result = FunnelAnalyzer.analyze(
            twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000), breakdownDimension = "platform",
        )

        val breakdown = result.breakdown!!
        assertEquals("platform", breakdown.dimension)
        val android = breakdown.values.first { it.value == "android" }
        val ios = breakdown.values.first { it.value == "ios" }
        assertEquals(2, android.entered)
        assertEquals(1.0, android.overallConversion)
        assertEquals(1, ios.entered)
        assertEquals(0.0, ios.overallConversion)
    }

    @Test
    fun `no breakdown requested means no breakdown in the result`() {
        val rows = listOf(row("A", "screen_view", 0, "signup"), row("A", "signup_submitted", 10))
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000))
        assertNull(result.breakdown)
    }

    @Test
    fun `trend buckets actors by the day of their entry`() {
        val dayZero = 0L
        val dayOne = 86_400L
        val rows = listOf(
            row("A", "screen_view", dayZero, "signup"), row("A", "signup_submitted", dayZero + 10),
            row("B", "screen_view", dayOne, "signup"),
        )
        val result = FunnelAnalyzer.analyze(
            twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(dayOne + 1000), withTrend = true,
        )

        val trend = result.trend!!
        assertEquals(2, trend.size)
        assertEquals(1, trend[0].entered)
        assertEquals(1, trend[0].converted)
        assertEquals(1, trend[1].entered)
        assertEquals(0, trend[1].converted)
    }

    @Test
    fun `no trend requested means no trend in the result`() {
        val rows = listOf(row("A", "screen_view", 0, "signup"))
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000))
        assertNull(result.trend)
    }

    @Test
    fun `countedBy is install when every entered actor key is not a session fallback`() {
        val rows = listOf(row("install_hash_abc", "screen_view", 0, "signup"))
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000))
        assertEquals("install", result.countedBy)
    }

    @Test
    fun `countedBy is session when every entered actor key is a session fallback`() {
        val rows = listOf(row("sid:abc123", "screen_view", 0, "signup"))
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000))
        assertEquals("session", result.countedBy)
    }

    @Test
    fun `countedBy is mixed when entered actors are a blend`() {
        val rows = listOf(
            row("install_hash_abc", "screen_view", 0, "signup"),
            row("sid:xyz", "screen_view", 0, "signup"),
        )
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = rows, from = t(-1), to = t(1000))
        assertEquals("mixed", result.countedBy)
    }

    @Test
    fun `empty input has zero entered and no division-by-zero crash`() {
        val result = FunnelAnalyzer.analyze(twoSteps, windowSeconds = 3600, rows = emptyList(), from = t(-1), to = t(1000))
        assertEquals(0, result.entered)
        assertEquals(0, result.converted)
        assertEquals(0.0, result.overallConversion)
        assertTrue(result.steps.all { it.count == 0 })
    }
}
