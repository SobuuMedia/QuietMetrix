package com.quietmetrix.analytics.internal.transport

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventQueueTest {

    // Start each test from an empty queue. Other suites emit events through fire-and-forget
    // coroutines (e.g. ScreenTracker.closeOutAsync via QuietMetrix.stop) that can land in the
    // shared EventQueue after their own teardown, so an @AfterTest clear alone is not enough.
    @BeforeTest
    fun setUp() = runTest {
        EventQueue.configure(1000)
        EventQueue.clear()
    }

    @AfterTest
    fun tearDown() = runTest {
        EventQueue.clear()
    }

    @Test
    fun `enqueue adds event to queue`() = runTest {
        val event = EnqueuedEvent(
            event = "page_view",
            screen = "home",
            props = emptyMap(),
            sid = null,
            ts = kotlin.time.Clock.System.now(),
            wasOffline = false,
            sdk = null,
            ctx = null,
        )
        EventQueue.enqueue(event)
        assertEquals(1, EventQueue.size())
    }

    @Test
    fun `drain returns events and removes them from queue`() = runTest {
        val event = EnqueuedEvent(
            event = "test",
            screen = null,
            props = emptyMap(),
            sid = null,
            ts = kotlin.time.Clock.System.now(),
            wasOffline = false,
            sdk = null,
            ctx = null,
        )
        EventQueue.enqueue(event)
        EventQueue.enqueue(event.copy(event = "test2"))
        assertEquals(2, EventQueue.size())

        val drained = EventQueue.drain(10)
        assertEquals(2, drained.size)
        assertEquals("test", drained[0].event)
        assertEquals("test2", drained[1].event)
        assertTrue(EventQueue.isEmpty())
    }

    @Test
    fun `drain respects maxBatchSize`() = runTest {
        repeat(15) { i ->
            EventQueue.enqueue(EnqueuedEvent(
                event = "event_$i",
                screen = null,
                props = emptyMap(),
                sid = null,
                ts = kotlin.time.Clock.System.now(),
                wasOffline = false,
                sdk = null,
                ctx = null,
            ))
        }
        val drained = EventQueue.drain(10)
        assertEquals(10, drained.size)
        assertEquals(5, EventQueue.size())
    }

    @Test
    fun `overflow drops oldest when maxQueueSize reached`() = runTest {
        EventQueue.configure(5)
        repeat(7) { i ->
            EventQueue.enqueue(EnqueuedEvent(
                event = "event_$i",
                screen = null,
                props = emptyMap(),
                sid = null,
                ts = kotlin.time.Clock.System.now(),
                wasOffline = false,
                sdk = null,
                ctx = null,
            ))
        }
        assertEquals(5, EventQueue.size())
        val events = EventQueue.drain(10)
        assertEquals("event_2", events[0].event)
        assertEquals("event_6", events[4].event)
        EventQueue.configure(1000)
    }

    @Test
    fun `reEnqueue puts events back at front`() = runTest {
        val event = EnqueuedEvent(
            event = "test",
            screen = null,
            props = emptyMap(),
            sid = null,
            ts = kotlin.time.Clock.System.now(),
            wasOffline = false,
            sdk = null,
            ctx = null,
        )
        EventQueue.enqueue(event)
        val drained = EventQueue.drain(10)
        EventQueue.reEnqueue(drained)
        assertEquals(1, EventQueue.size())
    }

    @Test
    fun `clear empties the queue`() = runTest {
        repeat(5) {
            EventQueue.enqueue(EnqueuedEvent(
                event = "e",
                screen = null,
                props = emptyMap(),
                sid = null,
                ts = kotlin.time.Clock.System.now(),
                wasOffline = false,
                sdk = null,
                ctx = null,
            ))
        }
        EventQueue.clear()
        assertTrue(EventQueue.isEmpty())
    }
}
