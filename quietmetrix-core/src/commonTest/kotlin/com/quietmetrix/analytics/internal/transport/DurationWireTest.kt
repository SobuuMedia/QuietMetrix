package com.quietmetrix.analytics.internal.transport

import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.ScreenTracker
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DurationWireTest {
    private val t0 = Instant.fromEpochMilliseconds(1_000_000)

    @BeforeTest
    fun setUp() = runTest {
        ConfigHolder.reset(); InMemoryStore.clear()
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_", trackingAllowedByDefault = true))
        ScreenTracker.reset(); EventQueue.clear()
    }

    @AfterTest
    fun tearDown() = runTest {
        ScreenTracker.reset(); QuietMetrix.stop(); EventQueue.clear()
        ConfigHolder.reset(); InMemoryStore.clear()
    }

    @Test
    fun `screen_view duration_ms serializes as a JSON number`() = runTest {
        ScreenTracker.enter("Home", now = t0)
        ScreenTracker.enter("Cart", now = t0.plus(5.seconds))   // emits screen_view(Home, 5000)
        val events = EventQueue.drain(10)
        val json = HttpTransport.serializeBatch(events)
        // PHP normalizeDuration() only accepts an int/float, NOT a quoted string.
        assertTrue(json.contains("\"duration_ms\":5000"), "expected numeric duration_ms, got: $json")
    }
}
