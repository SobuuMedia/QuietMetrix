package com.quietmetrix.analytics.internal.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class HttpTransportTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializeBatch with single event produces valid JSON`() {
        val event = EnqueuedEvent(
            event = "page_view",
            screen = "home",
            props = mapOf("title" to "Dashboard"),
            sid = "abc",
            ts = Clock.System.now(),
            wasOffline = false,
            sdk = SdkInfo("jvm", "1.0.0"),
            ctx = EventContext(language = "en"),
        )

        val result = HttpTransport.serializeBatch(listOf(event))
        val parsed: TrackEventRequestDto = json.decodeFromString(result)

        assertEquals("page_view", parsed.event)
        assertEquals("home", parsed.screen)
        assertEquals("Dashboard", parsed.props?.get("title")?.jsonPrimitive?.content)
        assertEquals("abc", parsed.sid)
        assertNotNull(parsed.ts)
    }

    @Test
    fun `serializeBatch with two events uses array wrapper`() {
        val event1 = EnqueuedEvent(
            event = "click",
            screen = null,
            props = emptyMap(),
            sid = null,
            ts = Clock.System.now(),
            wasOffline = false,
            sdk = null,
            ctx = null,
        )
        val event2 = EnqueuedEvent(
            event = "scroll",
            screen = null,
            props = emptyMap(),
            sid = null,
            ts = Clock.System.now(),
            wasOffline = false,
            sdk = null,
            ctx = null,
        )

        val result = HttpTransport.serializeBatch(listOf(event1, event2))
        val parsed: TrackBatchRequestDto = json.decodeFromString(result)

        assertEquals(2, parsed.events.size)
        assertEquals("click", parsed.events[0].event)
        assertEquals("scroll", parsed.events[1].event)
    }

    @Test
    fun `single event serialization includes was_offline flag when true`() {
        val event = EnqueuedEvent(
            event = "offline_action",
            screen = null,
            props = emptyMap(),
            sid = null,
            ts = Clock.System.now(),
            wasOffline = true,
            sdk = null,
            ctx = null,
        )

        val result = HttpTransport.serializeBatch(listOf(event))
        val obj = json.parseToJsonElement(result).jsonObject

        val wasOfflineField = obj["was_offline"]
        assertNotNull(wasOfflineField)
        assertEquals("true", wasOfflineField.jsonPrimitive.content)
    }

    @Test
    fun `serialized event never carries a uid field`() {
        val event = EnqueuedEvent(
            event = "purchase",
            screen = null,
            props = emptyMap(),
            sid = null,
            ts = Clock.System.now(),
            wasOffline = false,
            sdk = null,
            ctx = EventContext(anonymousId = "qm_aid_deadbeef"),
        )

        val result = HttpTransport.serializeBatch(listOf(event))
        val obj = json.parseToJsonElement(result).jsonObject

        assertNull(obj["uid"], "the wire format must not expose a user id")
    }

    @Test
    fun `anonymous id is nested under ctx not at the top level`() {
        val event = EnqueuedEvent(
            event = "page_view",
            ts = Clock.System.now(),
            ctx = EventContext(anonymousId = "qm_aid_deadbeef"),
        )

        val result = HttpTransport.serializeBatch(listOf(event))
        val obj = json.parseToJsonElement(result).jsonObject

        // Both servers read the install id from ctx.anonymous_id — a top-level field is dropped.
        assertNull(obj["anonymous_id"], "anonymous_id must not sit at the top level")
        val ctx = obj["ctx"]?.jsonObject
        assertNotNull(ctx)
        assertEquals("qm_aid_deadbeef", ctx["anonymous_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `toRequest carries the anonymous id into the wire context`() {
        val event = EnqueuedEvent(
            event = "page_view",
            ts = Clock.System.now(),
            ctx = EventContext(language = "en", anonymousId = "qm_aid_cafe"),
        )

        val request = event.toRequest()
        assertEquals("qm_aid_cafe", request.ctx?.anonymous_id)
    }

    @Test
    fun `toRequest maps a blank anonymous id to null on the wire`() {
        // DeviceContext.anonymousId is "" (not null) when the app has disabled collection via
        // QuietMetrixConfig.collectAnonymousId=false — this boundary is where that becomes a real
        // absence on the wire, rather than shipping a constant empty-string id to every consumer.
        val event = EnqueuedEvent(
            event = "page_view",
            ts = Clock.System.now(),
            ctx = EventContext(language = "en", anonymousId = ""),
        )

        val request = event.toRequest()
        assertNull(request.ctx?.anonymous_id)
    }

    @Test
    fun `toRequest maps all-null prop values to null`() {
        val event = EnqueuedEvent(
            event = "test",
            screen = null,
            props = mapOf("a" to null, "b" to null),
            sid = null,
            ts = Clock.System.now(),
            wasOffline = false,
            sdk = null,
            ctx = null,
        )

        val request = event.toRequest()
        assertEquals(null, request.props)
    }

    @Test
    fun `toRequest converts non-null prop values to strings`() {
        val event = EnqueuedEvent(
            event = "test",
            screen = null,
            props = mapOf("count" to 42, "flag" to true, "name" to "hello"),
            sid = null,
            ts = Clock.System.now(),
            wasOffline = false,
            sdk = null,
            ctx = null,
        )

        val request = event.toRequest()
        assertNotNull(request.props)
        assertEquals("42", request.props!!["count"]?.jsonPrimitive?.content)
        assertEquals("true", request.props!!["flag"]?.jsonPrimitive?.content)
        assertEquals("hello", request.props!!["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `toRequest carries ctx country into the wire context`() {
        val event = EnqueuedEvent(
            event = "page_view",
            ts = Clock.System.now(),
            ctx = EventContext(language = "en", country = "US"),
        )

        val request = event.toRequest()
        assertEquals("US", request.ctx?.country)
        assertEquals("en", request.ctx?.language)
    }

    @Test
    fun `serialized output is parseable JSON`() {
        val event = EnqueuedEvent(
            event = "load",
            screen = "splash",
            props = mapOf("duration" to 1200L),
            sid = "s1",
            ts = Clock.System.now(),
            wasOffline = false,
            sdk = SdkInfo("jvm", "2.0.0"),
            ctx = EventContext(language = "fr", ua = "test-agent", anonymousId = "qm_aid_1"),
        )

        val result = HttpTransport.serializeBatch(listOf(event))

        val parsed: TrackEventRequestDto = json.decodeFromString(result)
        assertEquals("load", parsed.event)
        assertEquals("splash", parsed.screen)
        assertEquals("1200", parsed.props?.get("duration")?.jsonPrimitive?.content)
        assertEquals("s1", parsed.sid)
        assertEquals("qm_aid_1", parsed.ctx?.anonymous_id)
    }
}
