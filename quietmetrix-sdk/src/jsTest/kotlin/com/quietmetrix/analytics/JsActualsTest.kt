package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.context.DeviceContext
import com.quietmetrix.analytics.internal.createPersistentStore
import com.quietmetrix.analytics.internal.transport.ConnectivityMonitor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the js(IR) actuals under the Node test runner, where no real browser globals exist.
 * Storage-backed actuals use the [InMemoryStore] fallback (same as the JVM/iOS targets); the
 * navigator/window lookups return their SSR-safe defaults.
 */
class JsActualsTest {

    @BeforeTest
    fun setUp() {
        ConfigHolder.reset()
        InMemoryStore.clear()
    }

    @AfterTest
    fun tearDown() {
        runCatching { QuietMetrix.stop() }
        ConfigHolder.reset()
        InMemoryStore.clear()
    }

    @Test
    fun persistentStore_roundTrips() {
        val store = createPersistentStore("qm_test_")
        assertNull(store.get("k"))
        store.set("k", "v")
        assertEquals("v", store.get("k"))
        store.remove("k")
        assertNull(store.get("k"))
    }

    @Test
    fun cookieConsent_persistsAndGatesTracking() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "qm_consent_", trackingEndpoint = null))

        setCookieConsent(true)
        assertTrue(hasCookieConsent())
        assertTrue(isTrackingAllowed())

        setCookieConsent(false)
        assertFalse(isTrackingAllowed())
    }

    @Test
    fun deviceContext_reportsPlatformAndAnonymousId() {
        // anonymousId is only generated once the SDK is initialized (it needs a storage prefix).
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "qm_device_", trackingEndpoint = null))
        val ctx = DeviceContext()
        assertEquals("js", ctx.platform)
        assertTrue(ctx.anonymousId.startsWith("qm_aid_"))
    }

    @Test
    fun connectivityMonitor_defaultsOnline() {
        assertTrue(ConnectivityMonitor().isOnline)
    }
}
