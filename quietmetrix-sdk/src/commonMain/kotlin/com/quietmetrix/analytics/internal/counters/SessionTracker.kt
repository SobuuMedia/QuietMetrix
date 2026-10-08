package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.Gate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Persists an anonymous visit at foreground start; completion/duration close at background.
 * No session identifier or event trail leaves the device. A killed foreground session keeps
 * its visit contribution, while unfinished duration is intentionally omitted.
 */
@OptIn(ExperimentalTime::class)
internal object SessionTracker {
    private var startedAt: Instant? = null
    private val mutex = Mutex()

    suspend fun start(now: Instant = Clock.System.now()): Unit = mutex.withLock {
        if (!Gate.shouldTrack()) { startedAt = null; return@withLock }
        if (startedAt != null) return@withLock
        // Persist the visit promptly; an OS kill must not require a shutdown callback.
        MetricGateway.record("visit_start_v2", emptyMap(), now = now)
        startedAt = now
    }

    suspend fun stop(now: Instant = Clock.System.now()): Unit = mutex.withLock {
        val start = startedAt ?: return@withLock
        startedAt = null
        if (!Gate.shouldTrack()) return@withLock
        val durationMs = (now.toEpochMilliseconds() - start.toEpochMilliseconds()).coerceAtLeast(0L)
        MetricGateway.record("session", mapOf("bucket" to sessionBucket(durationMs)), now = now)
        if (durationMs > 0) {
            MetricGateway.record("visit_duration_ms_v2", emptyMap(), n = durationMs, now = now)
            MetricGateway.record("visit_complete_v2", emptyMap(), now = now)
        }
    }

    /** Test-only: clears in-memory state so tests don't leak a started session into one another. */
    internal fun reset() {
        startedAt = null
    }
}
