package com.quietmetrix.analytics.internal.funnels

import com.quietmetrix.analytics.Funnel
import com.quietmetrix.analytics.FunnelCountMode
import com.quietmetrix.analytics.FunnelIdentityScope
import com.quietmetrix.analytics.FunnelManifest
import com.quietmetrix.analytics.FunnelStep
import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.createPersistentStore
import com.quietmetrix.analytics.internal.transport.platformGet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RemoteFunnelsResponse(
    val namespace: String = "dashboard",
    @SerialName("cache_ttl_seconds") val cacheTtlSeconds: Int = 300,
    val funnels: List<RemoteFunnel> = emptyList(),
)

@Serializable
private data class RemoteFunnel(
    @SerialName("funnel_key") val key: String,
    val revision: Int,
    val steps: List<RemoteStep>,
    @SerialName("window_seconds") val windowSeconds: Long,
    @SerialName("count_mode") val countMode: String = "actor",
    @SerialName("identity_scope") val identityScope: String = "install_or_session",
    @SerialName("correlation_property") val correlationProperty: String? = null,
)

@Serializable
private data class RemoteStep(
    val key: String,
    val event: String,
    val name: String? = null,
    val screen: String? = null,
    val props: Map<String, String> = emptyMap(),
)

/** Dashboard definitions are cached locally and refreshed in the background. */
internal object FunnelRemoteClient {
    private const val CACHE_SUFFIX = "dashboard_funnels_config_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun loadCached(config: QuietMetrixConfig) {
        FunnelEvaluator.replaceRemoteManifests(cachedManifests(config))
    }

    fun refreshInBackground(config: QuietMetrixConfig) {
        if (!Gate.shouldTrack()) return
        val endpoint = endpoint(config.trackingEndpoint) ?: return
        val key = config.apiKey ?: return
        scope.launch {
            val body = runCatching { platformGet(endpoint, key) }.getOrNull() ?: return@launch
            val parsed = runCatching { json.decodeFromString<RemoteFunnelsResponse>(body) }.getOrNull() ?: return@launch
            val manifests = parsed.toManifests() ?: return@launch
            createPersistentStore(config.storageKeyPrefix).set(config.storageKeyPrefix + CACHE_SUFFIX, body)
            FunnelEvaluator.replaceRemoteManifests(manifests)
        }
    }

    fun cachedManifests(config: QuietMetrixConfig): List<FunnelManifest> {
        val body = runCatching { createPersistentStore(config.storageKeyPrefix).get(config.storageKeyPrefix + CACHE_SUFFIX) }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<RemoteFunnelsResponse>(body).toManifests() ?: emptyList() }.getOrDefault(emptyList())
    }

    private fun RemoteFunnelsResponse.toManifests(): List<FunnelManifest>? {
        if (namespace != "dashboard") return null
        if (funnels.size > 100) return null
        return funnels.mapNotNull { dto ->
            if (dto.key.isBlank() || dto.revision < 1 || dto.windowSeconds !in 1..(90L * 24 * 3600) || dto.steps.size !in 2..10) return@mapNotNull null
            val mode = when (dto.countMode) { "actor" -> FunnelCountMode.ACTOR; "attempt" -> FunnelCountMode.ATTEMPT; else -> return@mapNotNull null }
            val identity = when (dto.identityScope) {
                "install_or_session" -> FunnelIdentityScope.INSTALL_OR_SESSION
                "install" -> FunnelIdentityScope.INSTALL
                "session" -> FunnelIdentityScope.SESSION
                else -> return@mapNotNull null
            }
            if (mode == FunnelCountMode.ATTEMPT && dto.correlationProperty.isNullOrBlank()) return@mapNotNull null
            val funnel = Funnel(
                key = dto.key,
                name = dto.key,
                steps = dto.steps.map { FunnelStep(it.key, it.event, it.name, it.screen, it.props) },
                windowSeconds = dto.windowSeconds,
                countMode = mode,
                identityScope = identity,
                correlationProperty = dto.correlationProperty,
            )
            FunnelManifest("dashboard", dto.revision.toLong(), listOf(funnel))
        }
    }

    private fun endpoint(trackingEndpoint: String?): String? {
        val root = trackingEndpoint?.substringBeforeLast("/api/v1", missingDelimiterValue = "") ?: return null
        return if (root.isEmpty()) null else "$root/api/v2/sdk/funnels"
    }
}
