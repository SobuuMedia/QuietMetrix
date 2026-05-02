package com.quietmetrix.server.ingest

import kotlin.test.Test
import kotlin.test.assertEquals

class EventNormalizerTest {

    private val normalizer = EventNormalizer()

    @Test
    fun `normalize fills defaults for missing optional fields`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "page_view",
        )
        val event = normalizer.normalize(request, "42", null)
        assertEquals("42", event.projectId)
        assertEquals("page_view", event.eventName)
        assertEquals("unknown", event.deviceClass)
    }

    @Test
    fun `normalize classifies mobile user agent`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "tap",
            ctx = com.quietmetrix.server.domain.EventContext(
                ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8)",
            ),
        )
        val event = normalizer.normalize(request, "1", null)
        assertEquals("mobile", event.deviceClass)
    }

    @Test
    fun `normalize classifies desktop user agent`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "click",
            ctx = com.quietmetrix.server.domain.EventContext(
                ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
            ),
        )
        val event = normalizer.normalize(request, "1", null)
        assertEquals("desktop", event.deviceClass)
    }

    @Test
    fun `normalize classifies bot user agent`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "crawl",
            ctx = com.quietmetrix.server.domain.EventContext(
                ua = "Googlebot/2.1 (+http://www.google.com/bot.html)",
            ),
        )
        val event = normalizer.normalize(request, "1", null)
        assertEquals("bot", event.deviceClass)
    }

    @Test
    fun `normalize classifies tablet user agent`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "view",
            ctx = com.quietmetrix.server.domain.EventContext(
                ua = "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X)",
            ),
        )
        val event = normalizer.normalize(request, "1", null)
        assertEquals("tablet", event.deviceClass)
    }

    @Test
    fun `normalize preserves wasOffline flag`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "tap",
            wasOffline = true,
        )
        val event = normalizer.normalize(request, "1", null)
        assertEquals(true, event.wasOffline)
    }

    @Test
    fun `normalize truncates language to 10 chars`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "view",
            ctx = com.quietmetrix.server.domain.EventContext(
                language = "en-US-extra-long",
            ),
        )
        val event = normalizer.normalize(request, "1", null)
        assertEquals(10, event.language!!.length)
    }
}