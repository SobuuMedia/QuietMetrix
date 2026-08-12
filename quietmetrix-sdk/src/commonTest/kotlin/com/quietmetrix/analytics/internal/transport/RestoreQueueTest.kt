package com.quietmetrix.analytics.internal.transport

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The offline-replay path restores persisted [TrackEventRequestDto]s back into the queue.
 * These guard that the DTO -> [EnqueuedEvent] mapping preserves the full context — in
 * particular `country` and `ua`, which an earlier positional-constructor call silently dropped.
 */
class RestoreQueueTest {

    @Test
    fun `toEnqueued preserves ctx country and ua on replay`() {
        val dto = TrackEventRequestDto(
            event = "page_view",
            ts = "2026-07-03T00:00:00Z",
            ctx = EventContextDto(
                referrer = "https://ref",
                language = "en",
                ua = "test-agent",
                viewport = "800x600",
                country = "US",
                anonymous_id = "qm_aid_beef",
            ),
        )

        val restored = dto.toEnqueued()

        assertEquals("US", restored.ctx?.country)
        assertEquals("test-agent", restored.ctx?.ua)
        assertEquals("en", restored.ctx?.language)
        assertEquals("https://ref", restored.ctx?.referrer)
        assertEquals("800x600", restored.ctx?.viewport)
        assertEquals("qm_aid_beef", restored.ctx?.anonymousId)
    }

    @Test
    fun `toEnqueued carries through core fields`() {
        val dto = TrackEventRequestDto(
            event = "click",
            screen = "home",
            sid = "s1",
            ts = "2026-07-03T00:00:00Z",
            was_offline = true,
            sdk = SdkInfoDto("android", "0.2.0"),
        )

        val restored = dto.toEnqueued()

        assertEquals("click", restored.event)
        assertEquals("home", restored.screen)
        assertEquals("s1", restored.sid)
        assertEquals(true, restored.wasOffline)
        assertEquals("android", restored.sdk?.platform)
    }
}
