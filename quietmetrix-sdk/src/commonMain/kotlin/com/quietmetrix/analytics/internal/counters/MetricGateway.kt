package com.quietmetrix.analytics.internal.counters

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The single process-wide [MetricRecorder] every counter producer records into —
 * `trackEvent`, [com.quietmetrix.analytics.internal.ScreenTracker], and on-device funnel/
 * session tracking — and the only thing [CounterFlusher] drains from. Callers pass metric
 * name and dims; this computes the device's calendar day so producers never have to.
 */
@OptIn(ExperimentalTime::class)
internal object MetricGateway {
    private var recorder = MetricRecorder()
    private val purgeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend fun record(metric: String, dims: Map<String, String>, n: Long = 1L, now: Instant = Clock.System.now()) {
        recorder.record(metric, dims, day = utcTodayIso(now), n = n)
    }

    suspend fun drain(): List<MetricRecorder.PendingCounter> = recorder.drain()

    /** Discards any not-yet-flushed counters — called (fire-and-forget, mirroring the old
     *  `FlushManager.purgeQueue()`) when analytics is disabled mid-session, from a non-suspend
     *  call site ([com.quietmetrix.analytics.internal.Gate.setAnalyticsEnabled]). */
    fun purge() {
        purgeScope.launch { recorder.purge() }
    }

    /** Test-only: starts a fresh recorder so tests don't leak state into one another. */
    internal fun reset() {
        recorder = MetricRecorder()
    }
}
