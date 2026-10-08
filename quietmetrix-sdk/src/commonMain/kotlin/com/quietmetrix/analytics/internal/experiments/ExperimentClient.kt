package com.quietmetrix.analytics.internal.experiments

import com.quietmetrix.analytics.notifyExperimentDecisionObservers
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.createPersistentStore
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.transport.platformGet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Loads and refreshes the active-experiment config. Per-session snapshot semantics
 * (deliberate, not a bug): [loadSnapshot] reads whatever was cached from the *previous*
 * session into an immutable in-memory map used for this entire session, so a variant never
 * changes mid-session; [refreshInBackground] fetches fresh config and saves it for the *next*
 * session for the legacy experiment format. V2 definitions update the live snapshot after the
 * first valid fetch so placement elements can resolve in the same session. On a device's very
 * first launch (no cache yet), placements use their control fallback until that fetch succeeds. Mirrors
 * [com.quietmetrix.analytics.internal.funnels.FunnelRegistrar]'s fire-and-forget tolerance: a
 * failed fetch simply leaves the cached config stale, retried on the next launch.
 */
internal object ExperimentClient {

    private const val CONFIG_KEY_SUFFIX = "experiments_config"
    private const val V2_CONFIG_KEY_SUFFIX = "experiments_config_v2"

    private var snapshot: Map<String, ExperimentSpec> = emptyMap()
    private var v2Snapshot: Map<String, ExperimentV2ItemDto> = emptyMap()
    private var v2ConfigLoaded = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Loads the cached config (from the previous session, if any) into memory for this session. */
    fun loadSnapshot(config: QuietMetrixConfig) {
        val store = createPersistentStore(config.storageKeyPrefix)
        val cached = runCatching { store.get(configKey(config.storageKeyPrefix)) }.getOrNull()
        snapshot = cached?.let { parseExperimentConfig(it) } ?: emptyMap()
        val cachedV2 = runCatching { store.get(v2ConfigKey(config.storageKeyPrefix)) }.getOrNull()
        v2Snapshot = cachedV2?.let(::parseExperimentV2Config) ?: emptyMap()
        v2ConfigLoaded = cachedV2 != null && isValidExperimentV2ConfigJson(cachedV2)
        notifyExperimentDecisionObservers()
    }

    /** The experiment for [key] this session, or null if unknown/not fetched yet. */
    fun specFor(key: String): ExperimentSpec? = snapshot[key]

    fun v2SpecFor(key: String): ExperimentV2ItemDto? = v2Snapshot[key]
    fun activeV2SpecsForGoal(eventKey: String): List<ExperimentV2ItemDto> =
        v2Snapshot.values.filter { it.status == "active" && it.goalEvent == eventKey }
    fun isV2ConfigLoaded(): Boolean = v2ConfigLoaded

    /** Fetches fresh config in the background and persists it for the next session. */
    fun refreshInBackground(config: QuietMetrixConfig) {
        if (!Gate.shouldTrack()) return
        val endpoint = experimentsConfigEndpoint(config.trackingEndpoint)
        val apiKey = config.apiKey
        if (endpoint == null || apiKey == null) {
            v2ConfigLoaded = true
            notifyExperimentDecisionObservers()
            return
        }
        scope.launch {
            val body = try {
                platformGet(endpoint, apiKey)
            } catch (_: Exception) {
                null
            }
            if (body != null && isValidExperimentConfigJson(body)) {
                createPersistentStore(config.storageKeyPrefix).set(configKey(config.storageKeyPrefix), body)
            }
            val v2Endpoint = experimentsConfigV2Endpoint(config.trackingEndpoint)
            val v2Body = v2Endpoint?.let {
                try { platformGet(it, apiKey) } catch (_: Exception) { null }
            }
            if (v2Body != null && isValidExperimentV2ConfigJson(v2Body)) {
                createPersistentStore(config.storageKeyPrefix).set(v2ConfigKey(config.storageKeyPrefix), v2Body)
                v2Snapshot = parseExperimentV2Config(v2Body)
            }
            v2ConfigLoaded = true
            notifyExperimentDecisionObservers()
        }
    }

    /** Test-only: clears the in-memory snapshot so tests don't leak state into one another. */
    internal fun reset() {
        snapshot = emptyMap()
        v2Snapshot = emptyMap()
        v2ConfigLoaded = false
    }

    private fun configKey(prefix: String) = "$prefix$CONFIG_KEY_SUFFIX"
    private fun v2ConfigKey(prefix: String) = "$prefix$V2_CONFIG_KEY_SUFFIX"

    /** `.../track` -> `.../experiments/config`, mirroring the server's route layout (see
     *  CounterFlusher.counterEndpoint / FunnelRegistrar.registerEndpoint for the same derivation). */
    private fun experimentsConfigEndpoint(trackingEndpoint: String?): String? {
        val base = trackingEndpoint ?: return null
        val root = base.substringBeforeLast("/api/v1", missingDelimiterValue = "")
        if (root.isEmpty()) return null
        return "$root/api/v1/experiments/config"
    }

    private fun experimentsConfigV2Endpoint(trackingEndpoint: String?): String? {
        val base = trackingEndpoint ?: return null
        val root = base.substringBeforeLast("/api/v1", missingDelimiterValue = "")
        if (root.isEmpty()) return null
        return "$root/api/v2/sdk/experiments"
    }
}
