package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell

/** Shapes two Overview fields that have no direct 1:1 counter reading. Mirrors
 *  php-hosting's `overviewCounterAnalyze*()` functions — keep the two in sync. */
object OverviewCounterAnalyzer {

    /**
     * Approximates "top screens" from `screen_transition{from,to}` cells: there is no
     * standalone "screen viewed" counter, so this sums `n` by destination (`to`) across every
     * origin (`from`) a screen was reached from, and returns the top [limit] by that sum.
     */
    fun topScreens(cells: List<CounterCell>, limit: Int): List<Pair<String, Long>> =
        cells.groupBy { it.dims["to"] }
            .mapNotNull { (screen, group) -> screen?.let { it to group.sumOf { c -> c.n } } }
            .sortedByDescending { it.second }
            .take(limit)

    data class ScreenDurationResult(val screen: String, val count: Long, val avgMs: Long, val totalMs: Long)

    /** ms(midpoint) per `screen_dwell` bucket label — see the SDK's `dwellBucket.kt`. The
     *  open-ended top bucket has no true midpoint; 450_000ms is an arbitrary anchor, not a
     *  claim about actual dwell time in that bucket. */
    private val BUCKET_MIDPOINT_MS = mapOf(
        "0_5s" to 2_500L,
        "5_10s" to 7_500L,
        "10_30s" to 20_000L,
        "30_60s" to 45_000L,
        "60_300s" to 180_000L,
        "300s_plus" to 450_000L,
    )

    /**
     * Reconstructs an approximate average/total dwell time per screen from `screen_dwell
     * {screen, bucket}` cells — exact per-view durations no longer exist server-side under
     * aggregate-only ingest, so `avgMs`/`totalMs` are a weighted average of each bucket's
     * midpoint, not a true mean. `count` (total dwell observations for the screen) is exact.
     */
    fun screenDurations(cells: List<CounterCell>): List<ScreenDurationResult> =
        cells.groupBy { it.dims["screen"] }
            .mapNotNull { (screen, group) ->
                if (screen == null) return@mapNotNull null
                val count = group.sumOf { it.n }
                val totalMs = group.sumOf { cell ->
                    val midpoint = BUCKET_MIDPOINT_MS[cell.dims["bucket"]] ?: 0L
                    midpoint * cell.n
                }
                ScreenDurationResult(
                    screen = screen,
                    count = count,
                    avgMs = if (count > 0) totalMs / count else 0L,
                    totalMs = totalMs,
                )
            }
            .sortedByDescending { it.count }
}
