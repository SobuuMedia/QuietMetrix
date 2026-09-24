package com.quietmetrix.analytics.internal.experiments

import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.createPersistentStore
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
 * session, but never touches the in-memory snapshot. On a device's very first launch (no cache
 * yet), every [getVariant] call resolves "none" until the next launch. Mirrors
 * [com.quietmetrix.analytics.internal.funnels.FunnelRegistrar]'s fire-and-forget tolerance: a
 * failed fetch simply leaves the cached config stale, retried on the next launch.
 */
internal object ExperimentClient {

    private const val CONFIG_KEY_SUFFIX = "experiments_config"

    private var snapshot: Map<String, ExperimentSpec> = emptyMap()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Loads the cached config (from the previous session, if any) into memory for this session. */
    fun loadSnapshot(config: QuietMetrixConfig) {
        val store = createPersistentStore(config.storageKeyPrefix)
        val cached = store.get(configKey(config.storageKeyPrefix))
        snapshot = cached?.let { parseExperimentConfig(it) } ?: emptyMap()
    }

    /** The experiment for [key] this session, or null if unknown/not fetched yet. */
    fun specFor(key: String): ExperimentSpec? = snapshot[key]

    /** Fetches fresh config in the background and persists it for the next session. */
    fun refreshInBackground(config: QuietMetrixConfig) {
        val endpoint = experimentsConfigEndpoint(config.trackingEndpoint) ?: return
        val apiKey = config.apiKey ?: return
        scope.launch {
            val body = try {
                platformGet(endpoint, apiKey)
            } catch (_: Exception) {
                null
            } ?: return@launch
            if (isValidExperimentConfigJson(body)) {
                createPersistentStore(config.storageKeyPrefix).set(configKey(config.storageKeyPrefix), body)
            }
        }
    }

    /** Test-only: clears the in-memory snapshot so tests don't leak state into one another. */
    internal fun reset() {
        snapshot = emptyMap()
    }

    private fun configKey(prefix: String) = "$prefix$CONFIG_KEY_SUFFIX"

    /** `.../track` -> `.../experiments/config`, mirroring the server's route layout (see
     *  CounterFlusher.counterEndpoint / FunnelRegistrar.registerEndpoint for the same derivation). */
    private fun experimentsConfigEndpoint(trackingEndpoint: String?): String? {
        val base = trackingEndpoint ?: return null
        val root = base.substringBeforeLast("/api/v1", missingDelimiterValue = "")
        if (root.isEmpty()) return null
        return "$root/api/v1/experiments/config"
    }
}
