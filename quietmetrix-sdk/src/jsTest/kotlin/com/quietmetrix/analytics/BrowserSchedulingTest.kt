package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.counters.MetricGateway
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.async
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import com.quietmetrix.analytics.internal.Gate

class BrowserSchedulingTest {
    @AfterTest fun reset() {
        platformStop()
        ConfigHolder.reset()
        MetricGateway.reset()
        InMemoryStore.clear()
    }

    @Test fun `browser scheduling does not record an event while tracking is disabled`() = runTest {
        ConfigHolder.reset()
        MetricGateway.reset()
        platformInit(QuietMetrixConfig(storageKeyPrefix = "scheduling_disabled_", autoTrackInitialPageView = false))
        MetricGateway.record("event", mapOf("name" to "must_not_record"))
        assertTrue(MetricGateway.drain().isEmpty())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `revocation while a browser recording is yielding discards the event`() = runTest {
        ConfigHolder.reset()
        MetricGateway.reset()
        val config = QuietMetrixConfig(storageKeyPrefix = "scheduling_revoke_", autoTrackInitialPageView = false)
        ConfigHolder.set(config)
        setCookieConsent(true)
        assertTrue(Gate.shouldTrack())
        platformInit(config)
        val pending = async { MetricGateway.record("event", mapOf("name" to "revoked_event")) }
        runCurrent()
        setCookieConsent(false)
        assertFalse(Gate.shouldTrack())
        advanceUntilIdle()
        pending.await()
        assertTrue(MetricGateway.drain().none { it.dims["name"] == "revoked_event" })
    }
}
