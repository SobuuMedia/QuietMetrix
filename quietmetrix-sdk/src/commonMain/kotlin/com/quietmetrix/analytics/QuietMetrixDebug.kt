package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.counters.DebugState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read-only, live visibility into the counter pipeline's most recent flush — what was sent and
 * whether it succeeded. Meant for the optional `quietmetrix-sdk-debug` module's overlay UI, or
 * any host app that wants to build its own; this object carries no UI dependency itself.
 *
 * Only populated when [QuietMetrixConfig.debug] is `true` — [pending]/[lastFlush] and their
 * `current*()` snapshots stay empty/`null` in a production build, at zero runtime cost (the
 * underlying state holder is never written to).
 */
object QuietMetrixDebug {

    /** One counter cell as of the most recent flush attempt. [n] is the amount sent — an
     *  occurrence count for most metrics, a summed amount for `value{name}` — not a live,
     *  continuously-updating total. */
    data class PendingCounter(val metric: String, val dims: Map<String, String>, val n: Long, val day: String)

    /** [atEpochMs] is when the flush was attempted; [succeeded] is `false` if any batch in it
     *  failed to send (offline, non-2xx, timeout) — that batch was still dropped, not retried,
     *  per the counter pipeline's documented lack of a persistent retry queue. */
    data class FlushOutcome(val atEpochMs: Long, val counterCount: Int, val succeeded: Boolean)

    /** The counters included in the most recent flush attempt, or empty before the first one.
     *  A [Flow], not a `StateFlow` — Compose's `collectAsState(initial = currentPending())`
     *  (or any cold-flow collector) works fine against it without this module needing to hold
     *  a coroutine scope open just to re-publish [DebugState]'s values. */
    val pending: Flow<List<PendingCounter>>
        get() = DebugState.pending.map { list -> list.map { PendingCounter(it.metric, it.dims, it.n, it.day) } }

    /** The outcome of the most recent flush attempt, or `null` before the first one. */
    val lastFlush: Flow<FlushOutcome?>
        get() = DebugState.lastFlush.map { it?.toPublic() }

    /** Synchronous snapshot of [pending], for a one-shot read without collecting the [Flow]. */
    fun currentPending(): List<PendingCounter> =
        DebugState.pending.value.map { PendingCounter(it.metric, it.dims, it.n, it.day) }

    /** Synchronous snapshot of [lastFlush], for a one-shot read without collecting the [Flow]. */
    fun currentLastFlush(): FlushOutcome? = DebugState.lastFlush.value?.toPublic()

    private fun DebugState.FlushOutcome.toPublic() = FlushOutcome(atEpochMs, counterCount, succeeded)
}
