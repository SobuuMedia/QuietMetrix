package com.quietmetrix.analytics.internal.experiments

import com.quietmetrix.analytics.internal.counters.MetricGateway
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Records at most one impression and one interaction per experiment key, per session —
 * cleared by [reset], called from [com.quietmetrix.analytics.QuietMetrix.init] alongside
 * [com.quietmetrix.analytics.internal.counters.SessionTracker.start] (a "session" is one SDK
 * lifetime, same convention those use). New session-scoped in-memory state, not a reuse of any
 * existing dedup utility — [com.quietmetrix.analytics.internal.ScreenTracker] tracks only the
 * current screen, not a "seen" set.
 */
internal object ExperimentTracker {
    private val mutex = Mutex()
    private val impressedVariant = mutableMapOf<String, String>()
    private val interacted = mutableSetOf<String>()

    /** Records one `experiment{exp, variant, action=impression}` counter, the first time
     *  [key] is shown this session. A repeat call for the same [key] is a no-op. */
    suspend fun onImpression(key: String, variant: String) {
        val shouldRecord = mutex.withLock {
            if (impressedVariant.containsKey(key)) {
                false
            } else {
                impressedVariant[key] = variant
                true
            }
        }
        if (shouldRecord) {
            MetricGateway.record("experiment", mapOf("exp" to key, "variant" to variant, "action" to "impression"))
        }
    }

    /** Records one `experiment{exp, variant, action=interaction}` counter for whatever variant
     *  [key] was shown. A no-op if no impression was recorded this session, or if an
     *  interaction was already recorded for [key]. */
    suspend fun onInteraction(key: String) {
        val variant = mutex.withLock {
            if (key in interacted) return@withLock null
            val v = impressedVariant[key] ?: return@withLock null
            interacted += key
            v
        }
        if (variant != null) {
            MetricGateway.record("experiment", mapOf("exp" to key, "variant" to variant, "action" to "interaction"))
        }
    }

    /** Called once per [com.quietmetrix.analytics.QuietMetrix.init] — clears session state. */
    internal fun reset() {
        impressedVariant.clear()
        interacted.clear()
    }
}
