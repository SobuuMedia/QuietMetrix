package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuietMetrixTest {

    @BeforeTest
    fun setUp() {
        ConfigHolder.reset()
        com.quietmetrix.analytics.internal.resetTestPreferences()
    }

    @AfterTest
    fun tearDown() {
        QuietMetrix.stop()
        ConfigHolder.reset()
        com.quietmetrix.analytics.internal.resetTestPreferences()
    }

    @Test
    fun init_setsIsInitialized() {
        assertFalse(QuietMetrix.isInitialized)
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        assertTrue(QuietMetrix.isInitialized)
    }

    @Test
    fun consent_roundTrip_accept() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        assertFalse(hasCookieConsent())
        setCookieConsent(true)
        assertTrue(hasCookieConsent())
        assertTrue(isTrackingAllowed())
    }

    @Test
    fun consent_roundTrip_decline_blocksTracking() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        setCookieConsent(false)
        assertTrue(hasCookieConsent())  // a choice was made; "has" is about whether the user decided
        assertFalse(isTrackingAllowed())
    }

    @Test
    fun consent_default_allowsTrackingWhenTrue() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_", trackingAllowedByDefault = true))
        assertFalse(hasCookieConsent())
        assertTrue(isTrackingAllowed())
    }

    @Test
    fun consent_default_blocksTrackingWhenFalse() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_", trackingAllowedByDefault = false))
        assertFalse(hasCookieConsent())
        assertFalse(isTrackingAllowed())
    }

    @Test
    fun banner_unset_returnsFalse() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        assertFalse(isBannerDismissedThisMonth())
    }

    @Test
    fun banner_markThenCheck_returnsTrue() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "test_"))
        markBannerDismissed()
        assertTrue(isBannerDismissedThisMonth())
    }

    @Test
    fun storageKeyPrefix_isolatesConsumers() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "appA_"))
        setCookieConsent(true)
        // A different consumer with a different prefix should not see appA's state.
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "appB_"))
        assertFalse(hasCookieConsent())
        // appA's value is still there in storage; switching back proves prefix-keyed isolation.
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "appA_"))
        assertTrue(hasCookieConsent())
        assertEquals("1", com.quietmetrix.analytics.internal.PersistentPreferences.get("cookie_consent"))
    }
    @Test
    fun refusal_and_disable_survive_configuration_restart() {
        val config = QuietMetrixConfig(storageKeyPrefix = "restart_", trackingAllowedByDefault = true)
        QuietMetrix.init(config)
        setCookieConsent(false)
        QuietMetrix.setAnalyticsEnabled(false)
        ConfigHolder.reset()
        QuietMetrix.init(config)
        assertTrue(hasCookieConsent())
        assertFalse(isTrackingAllowed())
        assertFalse(QuietMetrix.isAnalyticsEnabled)
    }

}
