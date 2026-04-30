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
        InMemoryStore.clear()
    }

    @AfterTest
    fun tearDown() {
        ConfigHolder.reset()
        InMemoryStore.clear()
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
        assertEquals("1", InMemoryStore.get("appA_cookie_consent"))
    }
}
