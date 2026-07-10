package com.quietmetrix.analytics.internal

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
internal object SessionManager {
    private var sessionStartTime: Instant? = null
    private var sessionEventCount = 0
    private var currentSessionNumber = 0

    fun startSession(prefix: String): String {
        val numKey = StorageKeys.sessionNumber(prefix)
        currentSessionNumber = InMemoryStore.get(numKey)?.toIntOrNull()?.plus(1) ?: 1
        InMemoryStore.set(numKey, currentSessionNumber.toString())
        sessionStartTime = Clock.System.now()
        sessionEventCount = 0
        return generateSid(prefix)
    }

    fun recordEvent() { sessionEventCount++ }

    fun endSession(): Map<String, Any?> {
        val duration = sessionStartTime?.let {
            Clock.System.now().toEpochMilliseconds() - it.toEpochMilliseconds()
        }
        sessionStartTime = null
        return mapOf(
            "duration_ms" to (duration ?: 0L),
            "event_count" to sessionEventCount,
            "session_number" to currentSessionNumber,
        )
    }

    fun isActive(): Boolean = sessionStartTime != null
    fun eventCount(): Int = sessionEventCount
    fun sessionNumber(): Int = currentSessionNumber
}
