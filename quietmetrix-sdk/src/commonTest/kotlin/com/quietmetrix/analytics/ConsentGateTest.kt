package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.internal.counters.SessionTracker
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class ConsentGateTest {
    @Test
    fun `failed refusal writes block tracking even when storage still contains acceptance`() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "failed_refusal_", trackingAllowedByDefault = true))
        com.quietmetrix.analytics.internal.PersistentPreferences.factory = {
            object : com.quietmetrix.analytics.internal.PersistentStore {
                override fun get(key: String) = "1"
                override fun set(key: String, value: String) { error("disk full") }
                override fun remove(key: String) = Unit
            }
        }
        assertFailsWith<IllegalStateException> { setCookieConsent(false) }
        assertFalse(isTrackingAllowed())
        assertFailsWith<IllegalStateException> { QuietMetrix.setAnalyticsEnabled(false) }
        assertFalse(QuietMetrix.isAnalyticsEnabled)
    }
    @Test
    fun `corrupt persisted choices override allowed defaults and require consent again`() {
        com.quietmetrix.analytics.internal.resetTestPreferences()
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "corrupt_consent_", trackingAllowedByDefault = true))
        com.quietmetrix.analytics.internal.PersistentPreferences.set("cookie_consent", "corrupt")
        assertFalse(isTrackingAllowed())
        assertFalse(hasCookieConsent())
        com.quietmetrix.analytics.internal.PersistentPreferences.set("analytics_enabled", "corrupt")
        assertFalse(QuietMetrix.isAnalyticsEnabled)
    }
    @AfterTest
    fun tearDown() {
        ScreenTracker.closeOutAsync()
        SessionTracker.reset()
        QuietMetrix.stop()
        ConfigHolder.reset()
        com.quietmetrix.analytics.internal.resetTestPreferences()
        MetricGateway.reset()
    }

    @Test
    fun `initialization without consent records no screen visit or session counters`() = runTest {
        ConfigHolder.reset()
        com.quietmetrix.analytics.internal.resetTestPreferences()
        MetricGateway.reset()
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "consent_test_", trackingAllowedByDefault = false))

        trackScreen("home")
        QuietMetrix.stop()

        assertTrue(MetricGateway.drain().isEmpty())
    }
}
