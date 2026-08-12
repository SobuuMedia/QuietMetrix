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

    @Test
    fun defineFunnel_stepReturnsResolvingPromise(): Promise<Unit> {
        init(offlineOptions("qm_facade_funnel_"))
        val stepOptions: FunnelStepOptions = js("({ key: 'submit', event: 'signup_submitted' })")
        val funnelOptions: FunnelOptions = js("({ key: 'signup', name: 'Signup', steps: [stepOptions] })")
        val handle = defineFunnel(funnelOptions)
        return handle.step("submit")
    }

    @Test
    fun init_acceptsFunnelsWithoutThrowing() {
        val stepOptions: FunnelStepOptions = js("({ key: 'view', event: 'screen_view', screen: 'signup' })")
        val funnelOptions: FunnelOptions = js("({ key: 'signup', name: 'Signup', steps: [stepOptions] })")
        val options: QuietMetrixInitOptions = js("({ storageKeyPrefix: 'qm_facade_init_funnels_', trackingEndpoint: null, funnels: [funnelOptions] })")
        init(options)
        assertTrue(QuietMetrix.isInitialized)
    }
}
