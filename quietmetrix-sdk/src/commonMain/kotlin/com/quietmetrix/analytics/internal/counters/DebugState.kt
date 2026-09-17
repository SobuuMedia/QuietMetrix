package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.QuietMetrixDebug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The internal-facing half of [QuietMetrixDebug]: [CounterFlusher] writes here, and
 * [QuietMetrixDebug] re-exposes the same [StateFlow]s publicly so the optional
 * `quietmetrix-sdk-debug` module (a separate Gradle module, so it can only see public API) can
 * read them. Split this way so the *public* surface stays a single small, clearly-named object
 * ([QuietMetrixDebug]) rather than every internal counter type becoming public.
 *
 * [recordFlush] is called by [CounterFlusher] **only when `config.debug == true`** — a
 * production build with `debug = false` never touches these `MutableStateFlow`s at all, not
 * even to publish an unread value.
 */
@OptIn(ExperimentalTime::class)
internal object DebugState {

    data class PendingCounterInfo(val metric: String, val dims: Map<String, String>, val n: Long, val day: String)
    data class FlushOutcome(val atEpochMs: Long, val counterCount: Int, val succeeded: Boolean)

    private val _pending = MutableStateFlow<List<PendingCounterInfo>>(emptyList())
    val pending: StateFlow<List<PendingCounterInfo>> = _pending.asStateFlow()

    private val _lastFlush = MutableStateFlow<FlushOutcome?>(null)
    val lastFlush: StateFlow<FlushOutcome?> = _lastFlush.asStateFlow()

    fun recordFlush(counters: List<MetricRecorder.PendingCounter>, succeeded: Boolean, now: Instant = Clock.System.now()) {
        _pending.value = counters.map { PendingCounterInfo(it.metric, it.dims, it.n, it.day) }
        _lastFlush.value = FlushOutcome(now.toEpochMilliseconds(), counters.size, succeeded)
    }

    /** Test-only: clears published state so tests don't leak into one another. */
    internal fun reset() {
        _pending.value = emptyList()
        _lastFlush.value = null
    }
}
