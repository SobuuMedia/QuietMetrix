package com.quietmetrix.analytics.internal.transport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal object HttpTransport : Transport {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun send(endpoint: String, apiKey: String, events: List<EnqueuedEvent>): SendResult {
        // serializeBatch emits a single event object for size==1 and a {"events":[…]} envelope
        // otherwise. The server exposes a matching pair of routes — POST …/track for a single event,
        // POST …/track/batch for the envelope — so route each body to the endpoint it belongs to.
        // Posting a batch envelope to the single-event route returns 400 schema_violation.
        val url = if (events.size == 1) endpoint else "$endpoint/batch"
        return platformSend(url, apiKey, events)
    }

    internal fun serializeBatch(events: List<EnqueuedEvent>): String {
        if (events.size == 1) {
            return json.encodeToString(TrackEventRequestSerializer, events.first().toRequest())
        }
        return json.encodeToString(TrackBatchRequestSerializer, TrackBatchRequestDto(events.map { it.toRequest() }))
    }
}

@Serializable
data class TrackEventRequestDto(
    val event: String,
    val screen: String? = null,
    val props: JsonObject? = null,
    val sid: String? = null,
    val ts: String,
    val was_offline: Boolean = false,
    val uid: String? = null,
    val anonymous_id: String? = null,
    val os: String? = null,
    val os_version: String? = null,
    val browser: String? = null,
    val browser_version: String? = null,
    val device_model: String? = null,
    val screen_width: Int? = null,
    val screen_height: Int? = null,
    val session_number: Int? = null,
    val is_session_start: Boolean = false,
    val is_session_end: Boolean = false,
    val sdk: SdkInfoDto? = null,
    val ctx: EventContextDto? = null,
)

@Serializable
data class TrackBatchRequestDto(
    val events: List<TrackEventRequestDto>,
)

@Serializable
data class SdkInfoDto(
    val platform: String,
    val version: String,
)

@Serializable
data class EventContextDto(
    val referrer: String? = null,
    val language: String? = null,
    val ua: String? = null,
    val viewport: String? = null,
    val country: String? = null,
)

@OptIn(ExperimentalTime::class)
internal fun EnqueuedEvent.toRequest() = TrackEventRequestDto(
    event = event,
    screen = screen,
    props = props.filterValues { it != null }.let { filtered ->
        if (filtered.isEmpty()) null else {
            buildJsonObject {
                filtered.forEach { (k, v) ->
                    when (v) {
                        is Boolean -> put(k, v)
                        is Number -> put(k, v as Number)
                        is String -> put(k, v)
                        else -> put(k, v.toString())
                    }
                }
            }
        }
    },
    sid = sid,
    ts = ts.toString(),
    was_offline = wasOffline,
    uid = userId,
    anonymous_id = anonymousId,
    os = osName,
    os_version = osVersion,
    browser = browserName,
    browser_version = browserVersion,
    device_model = deviceModel,
    screen_width = screenWidth,
    screen_height = screenHeight,
    session_number = sessionNumber,
    is_session_start = isSessionStart,
    is_session_end = isSessionEnd,
    sdk = sdk?.let { SdkInfoDto(it.platform, it.version) },
    ctx = ctx?.let { EventContextDto(it.referrer, it.language, it.ua, it.viewport, it.country) },
)

internal expect suspend fun platformSend(endpoint: String, apiKey: String, events: List<EnqueuedEvent>): SendResult

internal val TrackEventRequestSerializer = TrackEventRequestDto.serializer()
internal val TrackBatchRequestSerializer = TrackBatchRequestDto.serializer()