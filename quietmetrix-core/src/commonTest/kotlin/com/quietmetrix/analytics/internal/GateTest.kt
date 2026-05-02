package com.quietmetrix.analytics.internal

import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.setCookieConsent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GateTest {

    @BeforeTest
    fun setUp() {
        ConfigHolder.reset()
        InMemoryStore.clear()
    }

    @AfterTest
    fun tearDown() {
        QuietMetrix.stop()
        ConfigHolder.reset()
        InMemoryStore.clear()
    }

    @Test
    fun `shouldTrack returns false when not initialized`() {
        assertFalse(Gate.shouldTrack())
    }

    @Test
    fun `shouldTrack returns true when initialized, consent accepted, analytics enabled`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        setCookieConsent(true)
        QuietMetrix.setAnalyticsEnabled(true)
        assertTrue(Gate.shouldTrack())
    }

    @Test
    fun `shouldTrack returns false when consent declined`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        setCookieConsent(false)
        QuietMetrix.setAnalyticsEnabled(true)
        assertFalse(Gate.shouldTrack())
    }

    @Test
    fun `shouldTrack returns false when analytics disabled`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        setCookieConsent(true)
        QuietMetrix.setAnalyticsEnabled(false)
        assertFalse(Gate.shouldTrack())
    }

    @Test
    fun `shouldTrack returns false when both consent declined and analytics disabled`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        setCookieConsent(false)
        QuietMetrix.setAnalyticsEnabled(false)
        assertFalse(Gate.shouldTrack())
    }

    @Test
    fun `shouldTrack returns true with default consent (trackingAllowedByDefault=true)`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_", trackingAllowedByDefault = true))
        assertTrue(Gate.shouldTrack())
    }

    @Test
    fun `shouldTrack returns false with default denied and no choice made`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_", trackingAllowedByDefault = false))
        assertFalse(Gate.shouldTrack())
    }

    @Test
    fun `shouldTrack returns true after consent accepted even if previously undecided with default denied`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_", trackingAllowedByDefault = false))
        assertFalse(Gate.shouldTrack())
        setCookieConsent(true)
        assertTrue(Gate.shouldTrack())
    }

    @Test
    fun `setAnalyticsEnabled to false persists and blocks tracking`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        setCookieConsent(true)
        assertTrue(Gate.shouldTrack())
        QuietMetrix.setAnalyticsEnabled(false)
        assertFalse(Gate.shouldTrack())
    }

    @Test
    fun `isAnalyticsEnabled defaults to true`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        assertTrue(QuietMetrix.isAnalyticsEnabled)
    }

    @Test
    fun `isAnalyticsEnabled returns false after setAnalyticsEnabled(false)`() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        QuietMetrix.setAnalyticsEnabled(false)
        assertFalse(QuietMetrix.isAnalyticsEnabled)
    }
}