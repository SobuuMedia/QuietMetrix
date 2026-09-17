package com.quietmetrix.server.counters

import com.quietmetrix.server.persistence.CounterRepository.CounterCell

/**
 * Shapes `/sessions` from `session{bucket}` cells (see the SDK's `SessionTracker`/
 * `sessionBucket()`) — no per-session duration exists server-side under aggregate-only
 * ingest, so `avgDurationSec` is a weighted average of each bucket's midpoint, not a true
 * mean. `totalSessions` is exact. Mirrors php-hosting's `sessionCounterAnalyze()`.
 */
object SessionCounterAnalyzer {

    /** ms(midpoint) per `session` bucket label — see the SDK's `SessionBucket.kt`. The
     *  open-ended top bucket has no true midpoint; 1_800_000ms is an arbitrary anchor, not a
     *  claim about actual session length in that bucket. */
    private val BUCKET_MIDPOINT_MS = mapOf(
        "0_10s" to 5_000L,
        "10_30s" to 20_000L,
        "30_60s" to 45_000L,
        "60_300s" to 180_000L,
        "300_900s" to 600_000L,
        "900s_plus" to 1_800_000L,
    )

    data class Result(val totalSessions: Long, val avgDurationSec: Int)

    fun analyze(cells: List<CounterCell>): Result {
        val total = cells.sumOf { it.n }
        if (total == 0L) return Result(0L, 0)
        val totalMs = cells.sumOf { cell -> (BUCKET_MIDPOINT_MS[cell.dims["bucket"]] ?: 0L) * cell.n }
        return Result(total, (totalMs / total / 1000L).toInt())
    }
}
