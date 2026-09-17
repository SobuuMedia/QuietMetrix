package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [OverviewCounterAnalyzer] shapes two Overview fields that have no direct 1:1 counter:
 * top_screens (approximated from `screen_transition{to}` — there is no standalone "screen
 * viewed" counter) and screen_durations (reconstructed from `screen_dwell{screen,bucket}`
 * bucket counts, since exact per-view durations no longer exist server-side).
 */
class OverviewCounterAnalyzerTest {

    private fun transitionCell(from: String, to: String, n: Long) = CounterCell(mapOf("from" to from, "to" to to), n, n)
    private fun dwellCell(screen: String, bucket: String, n: Long) = CounterCell(mapOf("screen" to screen, "bucket" to bucket), n, n)

    @Test
    fun `topScreens sums screen_transition n by destination, across every origin`() {
        val cells = listOf(
            transitionCell("Home", "Detail", 10),
            transitionCell("Search", "Detail", 5),
            transitionCell("Home", "Cart", 3),
        )
        val result = OverviewCounterAnalyzer.topScreens(cells, limit = 10)
        assertEquals(listOf("Detail" to 15L, "Cart" to 3L), result)
    }

    @Test
    fun `topScreens respects the limit`() {
        val cells = listOf(transitionCell("A", "X", 3), transitionCell("A", "Y", 2), transitionCell("A", "Z", 1))
        assertEquals(listOf("X" to 3L, "Y" to 2L), OverviewCounterAnalyzer.topScreens(cells, limit = 2))
    }

    @Test
    fun `screenDurations sums bucket counts into a total count per screen`() {
        val cells = listOf(
            dwellCell("Home", "0_5s", 6),
            dwellCell("Home", "5_10s", 4),
        )
        val result = OverviewCounterAnalyzer.screenDurations(cells)
        assertEquals(1, result.size)
        assertEquals("Home", result[0].screen)
        assertEquals(10L, result[0].count)
    }

    @Test
    fun `screenDurations approximates avgMs from bucket midpoints, weighted by count`() {
        // 6 observations at the 0_5s midpoint (2500ms) + 4 at the 5_10s midpoint (7500ms):
        // (6*2500 + 4*7500) / 10 = 4500ms.
        val cells = listOf(dwellCell("Home", "0_5s", 6), dwellCell("Home", "5_10s", 4))
        val result = OverviewCounterAnalyzer.screenDurations(cells).single()
        assertEquals(4_500L, result.avgMs)
        assertEquals(45_000L, result.totalMs)
    }

    @Test
    fun `screenDurations covers every declared bucket label`() {
        val buckets = listOf("0_5s", "5_10s", "10_30s", "30_60s", "60_300s", "300s_plus")
        val cells = buckets.map { dwellCell("Screen", it, 1) }
        val result = OverviewCounterAnalyzer.screenDurations(cells).single()
        assertEquals(6L, result.count)
    }

    @Test
    fun `screenDurations sorts by count descending`() {
        val cells = listOf(dwellCell("Popular", "0_5s", 100), dwellCell("Rare", "0_5s", 1))
        val result = OverviewCounterAnalyzer.screenDurations(cells)
        assertEquals(listOf("Popular", "Rare"), result.map { it.screen })
    }
}
