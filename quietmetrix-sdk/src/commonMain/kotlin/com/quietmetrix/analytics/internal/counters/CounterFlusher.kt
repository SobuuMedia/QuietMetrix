package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.SDK_VERSION
import com.quietmetrix.analytics.internal.context.DeviceContext
import com.quietmetrix.analytics.internal.transport.platformPost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class CounterItemDto(
    val m: String,
    val d: Map<String, String> = emptyMap(),
    val n: Long,
    val u: Int = 0,
)

@Serializable
internal data class CounterSdkInfoDto(val platform: String, val version: String)

@Serializable
internal data class CounterAppInfoDto(val version: String? = null, val country: String? = null)

@Serializable
internal data class CounterBatchRequestDto(
    val sdk: CounterSdkInfoDto? = null,
    val app: CounterAppInfoDto? = null,
    val day: String,
    val counters: List<CounterItemDto>,
)

/**
 * Periodically drains [MetricGateway] and sends the result to `POST .../api/v1/counters` via
 * the same generic [platformPost] primitive funnel registration uses — no new per-platform
 * transport is needed, since a counter batch is just another JSON body. Fire-and-forget, like
 * [com.quietmetrix.analytics.internal.funnels.FunnelRegistrar]: a failed send simply drops
 * that batch (see the class-level note on [MetricGateway] / [MetricRecorder] about the
 * accepted, documented lack of a persistent retry queue for this first milestone).
 */
internal object CounterFlusher {
    private val json = Json { encodeDefaults = true }
    private var scope: CoroutineScope? = null
    private var flushJob: Job? = null

    fun start(config: QuietMetrixConfig) {
        val cs = scope ?: CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = cs
        flushJob?.cancel()
        flushJob = cs.launch {
            while (isActive) {
                delay(config.flushIntervalMs)
                flush(config)
            }
        }
    }

    /** Best-effort final flush before the periodic job is cancelled — the same tolerance as
     *  [com.quietmetrix.analytics.internal.ScreenTracker.closeOutAsync]: not guaranteed to
     *  complete before process exit, but attempted rather than silently dropped. */
    fun stop() {
        scope?.launch { ConfigHolder.configOrNull?.let { flush(it) } }
        flushJob?.cancel()
        flushJob = null
        scope = null
    }

    suspend fun flush(config: QuietMetrixConfig) {
        val endpoint = counterEndpoint(config.trackingEndpoint) ?: return
        val apiKey = config.apiKey ?: return
        val pending = MetricGateway.drain()
        if (pending.isEmpty()) return

        val device = DeviceContext()
        val batches = buildBatches(pending, device.platform, device.appVersion, SDK_VERSION)
        var allSucceeded = true
        for (batch in batches) {
            try {
                val ok = platformPost(endpoint, apiKey, json.encodeToString(CounterBatchRequestDto.serializer(), batch))
                if (!ok) allSucceeded = false
            } catch (_: Exception) {
                // Best-effort: see the class-level note on the lack of a retry queue.
                allSucceeded = false
            }
        }
        // Gated on debug so a production build never touches DebugState's MutableStateFlows —
        // see QuietMetrixDebug's doc comment for the "zero overhead when off" guarantee.
        if (config.debug) {
            DebugState.recordFlush(pending, allSucceeded)
        }
    }

    /** One request per distinct day in [pending] — normally just one, but a batch spanning a
     *  local-midnight rollover produces two rather than mis-tagging either day. */
    internal fun buildBatches(
        pending: List<MetricRecorder.PendingCounter>,
        platform: String,
        appVersion: String?,
        sdkVersion: String,
    ): List<CounterBatchRequestDto> =
        pending.groupBy { it.day }.map { (day, items) ->
            CounterBatchRequestDto(
                sdk = CounterSdkInfoDto(platform, sdkVersion),
                app = CounterAppInfoDto(version = appVersion),
                day = day,
                counters = items.map { CounterItemDto(it.metric, it.dims, it.n, if (it.isNewDevice) 1 else 0) },
            )
        }

    /** `.../track` or `.../track/batch` -> `.../counters`, mirroring the server's route
     *  layout (see FunnelRegistrar.registerEndpoint for the same derivation against
     *  `.../funnels/register`). */
    internal fun counterEndpoint(trackingEndpoint: String?): String? {
        val base = trackingEndpoint ?: return null
        val root = base.substringBeforeLast("/api/v1", missingDelimiterValue = "")
        if (root.isEmpty()) return null
        return "$root/api/v1/counters"
    }
}
