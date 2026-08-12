package com.quietmetrix.analytics.internal.funnels

import com.quietmetrix.analytics.Funnel
import com.quietmetrix.analytics.FunnelManifest
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.createPersistentStore
import com.quietmetrix.analytics.internal.transport.platformPost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class FunnelStepDto(
    val key: String,
    val event: String,
    val name: String? = null,
    val screen: String? = null,
    val props: Map<String, String> = emptyMap(),
)

@Serializable
internal data class FunnelDefinitionDto(
    val funnel_key: String,
    val name: String,
    val description: String? = null,
    val steps: List<FunnelStepDto>,
    val window_seconds: Long,
    val count_mode: String = "actor",
    val identity_scope: String = "install_or_session",
    val correlation_property: String? = null,
)

@Serializable
internal data class RegisterFunnelsRequestDto(
    val funnels: List<FunnelDefinitionDto>,
    val namespace: String? = null,
    val revision: Long? = null,
)

/**
 * Auto-registers [QuietMetrixConfig.funnelManifest] with the server on [QuietMetrix.init], once per
 * meaningful change. A funnel definition rarely changes between launches of the same app
 * version, so the canonical payload is fingerprinted and compared against the last one sent
 * (persisted so the comparison survives a process restart) — most launches make zero network
 * calls. Registration is fire-and-forget: a failure (offline, server error) simply leaves the
 * stored fingerprint stale, so the next launch retries. It never throws into the host app.
 */
internal object FunnelRegistrar {

    private val json = Json { encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun registerIfChanged(config: QuietMetrixConfig) {
        val manifest = config.funnelManifest
            ?: config.funnels.takeIf { it.isNotEmpty() }?.let { FunnelManifest("legacy", 0, it) }
            ?: return
        val endpoint = registerEndpoint(config.trackingEndpoint) ?: return
        val apiKey = config.apiKey ?: return

        val payload = canonicalPayload(manifest)
        val store = createPersistentStore(config.storageKeyPrefix)
        val fingerprintKey = "${config.storageKeyPrefix}funnels_fingerprint"
        val stored = store.get(fingerprintKey)
        if (!shouldRegister(payload, stored)) return

        scope.launch {
            val body = json.encodeToString(
                RegisterFunnelsRequestDto.serializer(),
                manifest.toDto(),
            )
            val success = try {
                platformPost(endpoint, apiKey, body)
            } catch (_: Exception) {
                false
            }
            if (success) {
                store.set(fingerprintKey, payload)
            }
        }
    }

    /**
     * Deterministic JSON of [funnels], sorted by key so declaration order in app code never
     * causes a spurious re-registration. Comparing this string across launches IS the
     * fingerprint — no separate hash is needed.
     */
    internal fun canonicalPayload(funnels: List<Funnel>): String {
        return canonicalPayload(FunnelManifest("legacy", 0, funnels))
    }

    internal fun canonicalPayload(manifest: FunnelManifest): String =
        json.encodeToString(RegisterFunnelsRequestDto.serializer(), manifest.toDto())

    internal fun shouldRegister(currentPayload: String, storedFingerprint: String?): Boolean =
        currentPayload != storedFingerprint

    /** `.../track` -> `.../funnels/register`, mirroring the server's route layout. */
    private fun registerEndpoint(trackingEndpoint: String?): String? {
        val base = trackingEndpoint ?: return null
        val root = base.substringBeforeLast("/api/v1", missingDelimiterValue = "")
        if (root.isEmpty()) return null
        return "$root/api/v1/funnels/register"
    }

    private fun Funnel.toDto() = FunnelDefinitionDto(
        funnel_key = key,
        name = name,
        description = description,
        steps = steps.map { FunnelStepDto(it.key, it.event, it.name, it.screen, it.props) },
        window_seconds = windowSeconds,
        count_mode = countMode.name.lowercase(),
        identity_scope = identityScope.name.lowercase(),
        correlation_property = correlationProperty,
    )

    private fun FunnelManifest.toDto() = RegisterFunnelsRequestDto(
        funnels = funnels.sortedBy { it.key }.map { it.toDto() },
        namespace = namespace,
        revision = revision,
    )
}
