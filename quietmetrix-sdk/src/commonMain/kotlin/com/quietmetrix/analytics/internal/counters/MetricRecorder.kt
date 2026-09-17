package com.quietmetrix.analytics.internal.counters

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Accumulates counter deltas in memory: `(metric, dims, day) -> n`. This is the on-device half
 * of the aggregate-only pipeline — the device does the analysis, and [drain] is the only
 * thing that ever leaves it, as a batch of [PendingCounter]s for [CounterFlusher] to send.
 *
 * [PendingCounter.isNewDevice] is the "first flush of this cell today" signal the server's
 * k-anonymity gate depends on (`counters` table's `devices` column) — no install identifier
 * is ever recorded or sent. It is computed once, at the moment a cell is first touched on a
 * given [day] (tracked by [seenCells], which spans drains — a cell already reported earlier
 * today stays not-new even after its accumulated count is flushed and reset to zero).
 *
 * Not itself scheduled or persisted: [CounterFlusher] owns when [drain] is called, and a
 * process restart mid-day starts [seenCells] fresh — a known, documented simplification (see
 * [CounterFlusher]) in the same conservative direction as a reinstall: it can inflate a
 * cell's device count, never deflate it below the true k-anonymity bound.
 */
internal class MetricRecorder {

    data class PendingCounter(
        val metric: String,
        val dims: Map<String, String>,
        val day: String,
        val n: Long,
        val isNewDevice: Boolean,
    )

    private data class Cell(val metric: String, val canonicalDims: String, val day: String)
    private class Accumulated(var n: Long, val isNewDevice: Boolean, val dims: Map<String, String>)

    private val mutex = Mutex()
    private val pending = LinkedHashMap<Cell, Accumulated>()

    /** Cells already known to have contributed on their day, spanning drains. Pruned so a
     *  long-running process doesn't grow this unboundedly. */
    private val seenCells = HashSet<Cell>()
    private var newestDaySeen: String? = null

    suspend fun record(metric: String, dims: Map<String, String>, day: String, n: Long = 1L) {
        mutex.withLock {
            val cell = Cell(metric, canonicalize(dims), day)
            val isNewDevice = seenCells.add(cell)
            val existing = pending[cell]
            if (existing == null) {
                pending[cell] = Accumulated(n, isNewDevice, dims)
            } else {
                existing.n += n
            }
            pruneSeenCells(day)
        }
    }

    suspend fun drain(): List<PendingCounter> = mutex.withLock {
        val result = pending.map { (cell, acc) -> PendingCounter(cell.metric, acc.dims, cell.day, acc.n, acc.isNewDevice) }
        pending.clear()
        result
    }

    /** Discards any not-yet-flushed counters without sending them — the counter-pipeline
     *  equivalent of the old event queue's purge-on-opt-out. [seenCells] is left alone: it
     *  never leaves the device, so keeping it just avoids re-declaring `isNewDevice` for a
     *  cell already reported today. */
    suspend fun purge() = mutex.withLock {
        pending.clear()
    }

    /** Sorted-key join with ASCII unit/record separators, matching
     *  CounterRegistry.kt/counterRegistry.php's canonicalization -- this is only ever an
     *  in-memory dedup key (never transmitted or hashed for storage), but a naive join
     *  without separators lets two different dims maps collide, e.g. {from:"AB",to:"C"} and
     *  {from:"A",to:"BC"}. */
    private fun canonicalize(dims: Map<String, String>): String =
        dims.entries.sortedBy { it.key }.joinToString(RECORD_SEP.toString()) { (key, value) -> "$key$UNIT_SEP$value" }

    private companion object {
        const val UNIT_SEP = '\u001F'
        const val RECORD_SEP = '\u001E'
    }

    /** Keeps [seenCells] from growing forever on a long-running process: once it's large,
     *  drop entries for days clearly older than the most recent one seen. A plain string
     *  comparison works because [day] is always ISO `yyyy-MM-dd`. */
    private fun pruneSeenCells(day: String) {
        if (day > (newestDaySeen ?: "")) newestDaySeen = day
        if (seenCells.size < 5_000) return
        val cutoff = newestDaySeen ?: return
        seenCells.retainAll { it.day >= cutoff }
    }
}
