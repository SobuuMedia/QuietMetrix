package com.quietmetrix.analytics.js

import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsFacadeTest {

    @BeforeTest
    fun setUp() {
        ConfigHolder.reset()
        InMemoryStore.clear()
    }

    // init() starts FlushManager's periodic timer; stop() cancels it so the Node test
    // process can exit instead of hanging on a live event loop.
    @AfterTest
    fun tearDown() {
        runCatching { QuietMetrix.stop() }
        ConfigHolder.reset()
        InMemoryStore.clear()
    }

    private fun offlineOptions(prefix: String): QuietMetrixInitOptions =
        js("({ storageKeyPrefix: prefix, trackingEndpoint: null })")

    @Test
    fun init_marksSdkInitialized() {
        init(offlineOptions("qm_facade_init_"))
        assertTrue(QuietMetrix.isInitialized)
    }

    @Test
    fun trackEvent_returnsResolvingPromise(): Promise<Unit> {
        init(offlineOptions("qm_facade_track_"))
        val opts: TrackOptions = js("({ screen: '/home', props: { plan: 'startup' } })")
        return trackEvent("page_view", opts)
    }

    @Test
    fun flush_returnsResolvingPromise(): Promise<Unit> {
        init(offlineOptions("qm_facade_flush_"))
        return flush()
    }

    @Test
    fun cookieConsent_gatesTrackingThroughFacade() {
        init(offlineOptions("qm_facade_consent_"))

        setCookieConsent(true)
        assertTrue(hasCookieConsent())
        assertTrue(isTrackingAllowed())

        setCookieConsent(false)
        assertFalse(isTrackingAllowed())
    }
}
