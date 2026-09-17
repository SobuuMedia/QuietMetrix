package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SessionCounterAnalyzer] shapes `/sessions` from `session{bucket}` cells — no per-session
 * duration exists server-side under aggregate-only ingest, so `avgDurationSec` is a weighted
 * average of each bucket's midpoint, not a true mean.
 */
class SessionCounterAnalyzerTest {

    private fun sessionCell(bucket: String, n: Long) = CounterCell(mapOf("bucket" to bucket), n, n)

    @Test
    fun `totalSessions sums n across every bucket`() {
        val cells = listOf(sessionCell("0_10s", 3), sessionCell("30_60s", 2))
        assertEquals(5L, SessionCounterAnalyzer.analyze(cells).totalSessions)
    }

    @Test
    fun `avgDurationSec approximates from bucket midpoints, weighted by count`() {
        // 3 sessions at the 0_10s midpoint (5000ms) + 2 at the 30_60s midpoint (45000ms):
        // (3*5000 + 2*45000) / 5 = 21000ms = 21s.
        val cells = listOf(sessionCell("0_10s", 3), sessionCell("30_60s", 2))
        assertEquals(21, SessionCounterAnalyzer.analyze(cells).avgDurationSec)
    }

    @Test
    fun `an empty range reports zero rather than dividing by zero`() {
        val result = SessionCounterAnalyzer.analyze(emptyList())
        assertEquals(0L, result.totalSessions)
        assertEquals(0, result.avgDurationSec)
    }

    @Test
    fun `covers every declared bucket label`() {
        val buckets = listOf("0_10s", "10_30s", "30_60s", "60_300s", "300_900s", "900s_plus")
        val cells = buckets.map { sessionCell(it, 1) }
        assertEquals(6L, SessionCounterAnalyzer.analyze(cells).totalSessions)
    }
}
