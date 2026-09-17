package com.quietmetrix.analytics.internal.counters

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Tracks one app session's wall-clock length in memory, from [start] ([QuietMetrix.init]) to
 * [stop] ([QuietMetrix.stop]), and reports it as a `session{bucket}` counter (see
 * [sessionBucket]) — no per-session identifier, start time, or event trail ever leaves the
 * device. In-memory only, like every other counter producer here: an app killed without a
 * clean [stop] (backgrounded and reclaimed by the OS, force-quit) never reports that session.
 */
@OptIn(ExperimentalTime::class)
internal object SessionTracker {
    private var startedAt: Instant? = null

    fun start(now: Instant = Clock.System.now()) {
        startedAt = now
    }

    suspend fun stop(now: Instant = Clock.System.now()) {
        val start = startedAt ?: return
        startedAt = null
        val durationMs = (now.toEpochMilliseconds() - start.toEpochMilliseconds()).coerceAtLeast(0L)
        MetricGateway.record("session", mapOf("bucket" to sessionBucket(durationMs)), now = now)
    }

    /** Test-only: clears in-memory state so tests don't leak a started session into one another. */
    internal fun reset() {
        startedAt = null
    }
}
