package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.isIsoCountryCode
import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.SDK_VERSION
import com.quietmetrix.analytics.internal.context.DeviceContext
import com.quietmetrix.analytics.internal.transport.platformPost
import com.quietmetrix.analytics.internal.transport.platformGet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    @SerialName("batch_id") val batchId: String? = null,
    val sdk: CounterSdkInfoDto? = null,
    val app: CounterAppInfoDto? = null,
    val day: String,
    val counters: List<CounterItemDto>,
)

@Serializable
internal data class CounterV2ItemDto(
    val m: String,
    val d: Map<String, String>,
    val n: Long,
    val contributors: Int,
)

@Serializable
internal data class CounterV2SegmentDto(val kind: String, val value: String)

@Serializable
internal data class CounterV2EnvelopeItemDto(
    val day: String,
    val hour: Int? = null,
    val metric: String,
    val segment: CounterV2SegmentDto,
    val dimensions: Map<String, String>,
    val n: Long,
)

@Serializable
internal data class CounterV2EnvelopeRequestDto(
    val schema: Int,
    @SerialName("batch_id") val batchId: String,
    val sdk: CounterSdkInfoDto,
    val items: List<CounterV2EnvelopeItemDto>,
)

@Serializable
internal data class CounterV2BatchRequestDto(
    @SerialName("batch_id") val batchId: String,
    val sdk: CounterSdkInfoDto,
    val app: CounterAppInfoDto,
    val day: String,
    val hour: Int? = null,
    val counters: List<CounterV2ItemDto>,
)

@Serializable
private data class CounterServerMetaDto(val capabilities: List<String> = emptyList())

/**
 * Persists each batch to a local outbox before sending. Its receipt ID stays fixed until the
 * server acknowledges it, so transient failures and process restarts safely retry the same body.
 */
internal object CounterFlusher {
    private val json = Json { encodeDefaults = true }
    private val metaJson = Json { ignoreUnknownKeys = true }
    private var scope: CoroutineScope? = null
    private var flushJob: Job? = null
    private var cachedMetaEndpoint: String? = null
    private var cachedCapabilities: Set<String>? = null
    private var cachedCapabilitiesAtEpochMs: Long = 0L

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
        pause()
        scope = null
    }

    fun pause() {
        flushJob?.cancel()
        flushJob = null
    }

    @OptIn(ExperimentalTime::class)
    suspend fun flush(config: QuietMetrixConfig) {
        if (!Gate.shouldTrack()) return
        val endpoint = counterEndpoint(config.trackingEndpoint) ?: return
        val apiKey = config.apiKey ?: return
        val capabilityResponse = serverCapabilities(endpoint, apiKey)
        val capabilities = capabilityResponse.orEmpty()
        val supportsReceipts = "counter_batch_receipts" in capabilities
        val supportsPeopleWindows = supportsReceipts && "unique_window_v2" in capabilities
        val supportsV2 = "counter_protocol_v2_hourly" in capabilities
        val supportsSchema2Envelope = "counter_protocol_v2_schema2" in capabilities
        val v2Endpoint = counterV2Endpoint(config.trackingEndpoint)
        val device = DeviceContext()
        val batches = MetricGateway.prepareBatches(
            device.platform, device.appVersion, normalizedCountry(config.countryCode), SDK_VERSION,
            Clock.System.now().toEpochMilliseconds(), device.deviceClass,
        )
        if (MetricGateway.hasPersistenceFailure()) return
        if (batches.isEmpty()) return
        // Never acknowledge or send a mixed legacy/v2 outbox batch until protocol support is
        // known: a temporary _meta outage must not silently discard hourly-only counters.
        if (capabilityResponse == null && batches.any { batch -> batch.counters.any { isV2OnlyMetric(it.metric) } }) return
        val pending = batches.flatMap { it.counters }
        var allSucceeded = !MetricGateway.hasPersistenceFailure()
        for (batch in batches) {
            val legacyCounters = countersForCapabilities(batch.counters, supportsPeopleWindows)
                .filterNot { isV2OnlyMetric(it.metric) }
            val v2Counters = if (supportsV2) batch.counters.filter { isV2Metric(it.metric) } else emptyList()
            var delivered = true
            if (legacyCounters.isNotEmpty()) {
                delivered = try {
                    val request = CounterBatchRequestDto(
                        batchId = batch.id,
                        sdk = CounterSdkInfoDto(batch.platform, batch.sdkVersion),
                        app = CounterAppInfoDto(version = batch.appVersion, country = batch.countryCode),
                        day = batch.day,
                        counters = legacyCounters.map { CounterItemDto(it.metric, it.dims, it.n, if (it.isNewDevice) 1 else 0) },
                    )
                    platformPost(endpoint, apiKey, json.encodeToString(CounterBatchRequestDto.serializer(), request))
                } catch (_: Exception) { false }
            }
            if (v2Counters.isNotEmpty()) {
                val sentV2 = try {
                    val url = v2Endpoint ?: error("v2 counter endpoint unavailable")
                    if (supportsSchema2Envelope) {
                        // Every child receipt is derived from the persisted immutable parent.
                        // Retrying a partially delivered parent reuses exactly the same IDs.
                        var complete = true
                        for (request in buildV2Envelopes(batch, v2Counters)) {
                            if (!platformPost(url, apiKey, json.encodeToString(CounterV2EnvelopeRequestDto.serializer(), request))) complete = false
                        }
                        complete
                    } else {
                        val request = buildV2Batch(batch, v2Counters)
                        platformPost(url, apiKey, json.encodeToString(CounterV2BatchRequestDto.serializer(), request))
                    }
                } catch (_: Exception) { false }
                delivered = delivered && sentV2
            }
            if (delivered || !supportsReceipts) MetricGateway.acknowledge(batch.id)
            if (!delivered) allSucceeded = false
        }
        // Gated on debug so a production build never touches DebugState's MutableStateFlows —
        // see QuietMetrixDebug's doc comment for the "zero overhead when off" guarantee.
        if (config.debug) {
            DebugState.recordFlush(pending, allSucceeded)
        }
    }

    private suspend fun serverCapabilities(counterEndpoint: String, apiKey: String): Set<String>? {
        val metaEndpoint = counterEndpoint.removeSuffix("/counters") + "/sdk/capabilities"
        val now = Clock.System.now().toEpochMilliseconds()
        if (cachedMetaEndpoint == metaEndpoint && cachedCapabilities != null &&
            now - cachedCapabilitiesAtEpochMs < CAPABILITY_CACHE_MS
        ) return cachedCapabilities
        val response = runCatching { platformGet(metaEndpoint, apiKey) }.getOrNull() ?: return null
        val metadata = runCatching { metaJson.decodeFromString<CounterServerMetaDto>(response) }.getOrNull() ?: return null
        cachedMetaEndpoint = metaEndpoint
        cachedCapabilities = metadata.capabilities.toSet()
        cachedCapabilitiesAtEpochMs = now
        return cachedCapabilities
    }

    internal fun countersForCapabilities(
        counters: List<MetricRecorder.PendingCounter>,
        supportsPeopleWindows: Boolean,
    ): List<MetricRecorder.PendingCounter> = counters.filter {
        supportsPeopleWindows || it.metric != "unique_window_v2"
    }

    internal fun isV2Metric(metric: String): Boolean = metric.endsWith("_v2") && metric in V2_METRICS

    internal fun isV2OnlyMetric(metric: String): Boolean = metric.endsWith("_v2") && metric !in V1_COMPATIBLE_V2_METRICS

    internal fun buildV2Batch(batch: MetricRecorder.QueuedBatch, counters: List<MetricRecorder.PendingCounter>): CounterV2BatchRequestDto =
        CounterV2BatchRequestDto(
            batchId = batch.id,
            sdk = CounterSdkInfoDto(batch.platform, batch.sdkVersion),
            app = CounterAppInfoDto(version = batch.appVersion, country = batch.countryCode),
            day = batch.day,
            hour = batch.hour,
            counters = counters.map { CounterV2ItemDto(it.metric, it.dims, it.n, if (it.isNewDevice) 1 else 0) },
        )

    internal fun buildV2Envelope(batch: MetricRecorder.QueuedBatch, counters: List<MetricRecorder.PendingCounter>): CounterV2EnvelopeRequestDto =
        CounterV2EnvelopeRequestDto(
            schema = 2,
            batchId = batch.id,
            sdk = CounterSdkInfoDto(batch.platform, batch.sdkVersion),
            items = counters.flatMap { counter ->
                val facets = buildList {
                    add(CounterV2SegmentDto("global", "all"))
                    val platform = batch.platform.lowercase().takeIf { it in setOf("android", "ios", "web", "desktop") } ?: "unknown"
                    add(CounterV2SegmentDto("platform", platform))
                    val scopeIsProject = counter.dims["scope_kind"] != "screen"
                    val facetMetric = if (counter.metric == "witness_window_v2") counter.dims["target_metric"] ?: counter.metric else counter.metric
                    val supportsFacets = facetMetric !in RESTRICTED_FACET_METRICS &&
                        (!counter.metric.startsWith("unique_") || scopeIsProject)
                    if (supportsFacets) {
                        add(CounterV2SegmentDto("country", batch.countryCode ?: "unknown"))
                        val deviceClass = batch.deviceClass.takeIf { it in setOf("phone", "tablet", "desktop", "unknown") } ?: "unknown"
                        add(CounterV2SegmentDto("device_class", deviceClass))
                    }
                }
                facets.map { segment -> CounterV2EnvelopeItemDto(
                    day = counter.day, hour = counter.hour, metric = counter.metric,
                    segment = segment, dimensions = counter.dims, n = counter.n,
                ) }
            },
        )

    internal fun buildV2Envelopes(batch: MetricRecorder.QueuedBatch, counters: List<MetricRecorder.PendingCounter>): List<CounterV2EnvelopeRequestDto> {
        val envelope = buildV2Envelope(batch, counters)
        if (envelope.items.size <= 512) return listOf(envelope)
        return envelope.items.chunked(512).mapIndexed { index, items ->
            envelope.copy(batchId = sha256Hex("${batch.id}:schema2:$index").take(32), items = items)
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

    internal fun counterV2Endpoint(trackingEndpoint: String?): String? {
        val base = trackingEndpoint ?: return null
        val root = base.substringBeforeLast("/api/v1", missingDelimiterValue = "")
        if (root.isEmpty()) return null
        return "$root/api/v2/counters"
    }

    internal fun normalizedCountry(countryCode: String?): String? = countryCode?.trim()?.uppercase()
        ?.takeIf(::isIsoCountryCode)

    private const val CAPABILITY_CACHE_MS = 24L * 60 * 60 * 1000
    private val V2_METRICS = setOf(
        "unique_window_v2", "unique_hour_v2", "witness_window_v2", "visit_start_v2", "visit_duration_ms_v2",
        "visit_complete_v2", "screen_view_v2", "screen_dwell_ms_v2", "screen_dwell_sample_v2", "event_v2",
        "crash_v2", "error_v2", "screen_transition_v2", "search_v2", "search_zero_result_v2", "retention_entry_v2",
        "retention_return_v2", "funnel_step_v2", "experiment_exposure_v2", "experiment_goal_v2", "sdk_presence_v2",
    )
    private val V1_COMPATIBLE_V2_METRICS = setOf("unique_window_v2", "experiment_exposure_v2", "experiment_goal_v2")
    private val RESTRICTED_FACET_METRICS = setOf(
        "retention_entry_v2", "retention_return_v2", "funnel_step_v2", "experiment_exposure_v2",
        "experiment_goal_v2", "sdk_presence_v2",
    )
}
