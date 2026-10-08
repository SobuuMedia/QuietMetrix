package com.quietmetrix.analytics.internal.experiments

import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.createPersistentStore
import com.quietmetrix.analytics.internal.Gate
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
    private val v2Mutex = Mutex()

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

    /** Persists once-per-installation/revision exposure state locally, then queues only an
     * aggregate count. No seed, identifier, or per-install assignment is sent to the server. */
    suspend fun onV2Exposure(key: String, revision: Int, variantId: String): Boolean {
        if (!Gate.shouldTrack()) return false
        val config = ConfigHolder.configOrNull ?: return false
        val marker = v2Marker("exposure", key, revision)
        val accepted = v2Mutex.withLock {
            val store = createPersistentStore(config.storageKeyPrefix)
            if (store.get(marker) != null) return@withLock false
            store.set(marker, variantId)
            true
        }
        if (!accepted) return false
        MetricGateway.record("experiment_exposure_v2", v2Dims(key, revision, variantId))
        return true
    }

    /** Counts a matching binary goal only after that installation recorded this revision's
     * exposure. The local marker prevents recomposition, repeated events, and app restarts from
     * incrementing the success more than once. */
    suspend fun onV2Goal(key: String, revision: Int, variantId: String): Boolean {
        if (!Gate.shouldTrack()) return false
        val config = ConfigHolder.configOrNull ?: return false
        val exposureMarker = v2Marker("exposure", key, revision)
        val goalMarker = v2Marker("goal", key, revision)
        val accepted = v2Mutex.withLock {
            val store = createPersistentStore(config.storageKeyPrefix)
            if (store.get(exposureMarker) != variantId || store.get(goalMarker) != null) return@withLock false
            store.set(goalMarker, "1")
            true
        }
        if (!accepted) return false
        MetricGateway.record("experiment_goal_v2", v2Dims(key, revision, variantId))
        return true
    }

    /** Called after trackEvent has accepted its event. It only records goals for tests that
     * are still active and whose local exposure ledger proves the same installation/revision
     * was previously exposed. */
    suspend fun onGoalEvent(eventKey: String) {
        if (!Gate.shouldTrack()) return
        val config = ConfigHolder.configOrNull ?: return
        val store = createPersistentStore(config.storageKeyPrefix)
        for (spec in ExperimentClient.activeV2SpecsForGoal(eventKey)) {
            val variant = store.get(v2Marker("exposure", spec.key, spec.revision)) ?: continue
            if (variant in setOf("a", "b")) onV2Goal(spec.key, spec.revision, variant)
        }
    }

    private fun v2Marker(kind: String, key: String, revision: Int) = "qm_experiment_v2_${kind}_${key}_$revision"
    private fun v2Dims(key: String, revision: Int, variantId: String) =
        mapOf("exp" to key, "rev" to revision.toString(), "variant" to variantId)

    /** Called once per [com.quietmetrix.analytics.QuietMetrix.init] — clears session state. */
    internal fun reset() {
        impressedVariant.clear()
        interacted.clear()
    }
}
