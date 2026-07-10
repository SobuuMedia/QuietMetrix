package com.quietmetrix.server.routes

import com.quietmetrix.server.domain.Event
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The dashboard `/events` shape must match the PHP backend so one EventRow model fits both. */
class EventsResponseMappingTest {

    private val event = Event(
        id = "5",
        projectId = "1",
        eventName = "page_view",
        screen = "Home",
        props = """{"a":1}""",
        sid = "sess-abc",
        ts = Instant.parse("2024-01-01T00:00:00Z"),
        wasOffline = true,
        country = "US",
    )

    @Test
    fun `uses php-compatible keys`() {
        val m = eventToEventsResponseMap(event)
        assertEquals("page_view", m["event_name"])
        assertEquals("sess-abc", m["session_id"])
        // Old Ktor-only keys must be gone.
        assertFalse(m.containsKey("event"))
        assertFalse(m.containsKey("sid"))
    }

    @Test
    fun `id is a numeric value not a prefixed string`() {
        assertEquals(5L, eventToEventsResponseMap(event)["id"])
    }

    @Test
    fun `props are decoded to a json object`() {
        assertTrue(eventToEventsResponseMap(event)["props"] is JsonObject)
    }

    @Test
    fun `includes per-visit duration_ms`() {
        assertEquals(8200L, eventToEventsResponseMap(event.copy(durationMs = 8200))["duration_ms"])
        assertEquals(null, eventToEventsResponseMap(event)["duration_ms"])
    }

    @Test
    fun `non-numeric id maps to null`() {
        assertEquals(null, eventToEventsResponseMap(event.copy(id = null))["id"])
    }
}
