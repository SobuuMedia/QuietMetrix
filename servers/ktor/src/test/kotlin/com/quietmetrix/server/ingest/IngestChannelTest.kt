package com.quietmetrix.server.ingest

import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

class IngestChannelTest {
    @Test
    fun `enqueue does not throw`() = runTest {
        val eventRepo = mockk<com.quietmetrix.server.persistence.EventRepository>(relaxed = true)
        val validator = EventValidator()
        val normalizer = EventNormalizer()
        val channel = IngestChannel(eventRepo, validator, normalizer)
        channel.start(this)

        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "test",
            screen = "home",
            props = null,
            sid = null,
            ts = java.time.Instant.now().toString(),
            wasOffline = false,
            sdk = null,
            ctx = null,
        )

        channel.enqueue("1", request)
        advanceUntilIdle()
        channel.stop()
        assertTrue(true)
    }
}
