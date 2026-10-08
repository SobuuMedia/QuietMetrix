package com.quietmetrix.analytics.internal.experiments

import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.getVariant
import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.internal.createPersistentStore
import com.quietmetrix.analytics.setCookieConsent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperimentEngineTest {

    @BeforeTest
    fun setUp() {
        ConfigHolder.reset()
        com.quietmetrix.analytics.internal.resetTestPreferences()
        MetricGateway.reset()
        ExperimentClient.reset()
        ExperimentTracker.reset()
    }

    @AfterTest
    fun tearDown() = runTest {
        ScreenTracker.reset() // avoids the documented ScreenTracker async-stop flake
        QuietMetrix.stop()
        ConfigHolder.reset()
        com.quietmetrix.analytics.internal.resetTestPreferences()
        MetricGateway.reset()
        ExperimentClient.reset()
        ExperimentTracker.reset()
    }

    private val configJson = """
        {"experiments":[{"key":"checkout_cta","traffic_percent":100,
          "variants":[{"name":"a","weight":50},{"name":"b","weight":50}],"countries":null}]}
    """.trimIndent()

    /** Seeds the cached config a previous session would have saved, then initializes with
     *  tracking allowed, exactly as an app that's granted consent would. */
    private fun initWithCachedConfig(prefix: String) {
        createPersistentStore(prefix).set("${prefix}experiments_config", configJson)
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = prefix, trackingEndpoint = null))
        setCookieConsent(true)
    }

    // ---- ExperimentTracker dedup, tested directly (deterministic, no fire-and-forget) ----

    @Test
    fun onImpression_calledTwice_recordsExactlyOneCounter() = runTest {
        ExperimentTracker.onImpression("checkout_cta", "a")
        ExperimentTracker.onImpression("checkout_cta", "a")

        val pending = MetricGateway.drain()
        val impressions = pending.filter { it.metric == "experiment" && it.dims["action"] == "impression" }
        assertEquals(1, impressions.size)
        assertEquals("checkout_cta", impressions[0].dims["exp"])
        assertEquals("a", impressions[0].dims["variant"])
    }

    @Test
    fun onInteraction_calledTwice_recordsExactlyOneCounter() = runTest {
        ExperimentTracker.onImpression("checkout_cta", "a")
        ExperimentTracker.onInteraction("checkout_cta")
        ExperimentTracker.onInteraction("checkout_cta")

        val pending = MetricGateway.drain()
        val interactions = pending.filter { it.metric == "experiment" && it.dims["action"] == "interaction" }
        assertEquals(1, interactions.size)
    }

    @Test
    fun onInteraction_withoutAPriorImpression_recordsNothing() = runTest {
        ExperimentTracker.onInteraction("checkout_cta")

        val pending = MetricGateway.drain()
        assertTrue(pending.none { it.metric == "experiment" })
    }

    // ---- getVariant()'s synchronous return-value contract ----

    @Test
    fun getVariant_snapshotStoredBeforeInit_returnsAnAssignedVariant() {
        initWithCachedConfig("engine_test1_")
        val variant = getVariant("checkout_cta")
        assertTrue(variant == "a" || variant == "b")
    }

    @Test
    fun getVariant_noCachedConfig_returnsNone() {
        QuietMetrix.init(QuietMetrixConfig(storageKeyPrefix = "engine_test2_", trackingEndpoint = null))
        setCookieConsent(true)
        assertEquals("none", getVariant("checkout_cta"))
    }

    @Test
    fun getVariant_unknownKey_returnsNone() {
        initWithCachedConfig("engine_test3_")
        assertEquals("none", getVariant("some_other_experiment"))
    }

    @Test
    fun getVariant_analyticsDisabled_returnsNone() {
        initWithCachedConfig("engine_test4_")
        QuietMetrix.setAnalyticsEnabled(false)
        assertEquals("none", getVariant("checkout_cta"))
    }

    @Test
    fun refreshInBackground_withNoTrackingEndpoint_doesNotChangeTheSessionSnapshot() {
        initWithCachedConfig("engine_test5_")
        val before = getVariant("checkout_cta")
        ExperimentClient.refreshInBackground(QuietMetrixConfig(storageKeyPrefix = "engine_test5_", trackingEndpoint = null))
        val after = getVariant("checkout_cta")
        assertEquals(before, after)
    }

    // NOTE: getVariant()/trackExperimentInteraction() fire-and-forget their counter recording
    // onto experimentsScope (a real Dispatchers.Default scope, deliberately not suspend so
    // Compose/SwiftUI callers can call them synchronously during rendering — see
    // events/Experiments.kt's doc comment). That hand-off itself isn't covered by a test here:
    // runBlocking isn't available on the JS target this suite also runs under, and a
    // real-time delay()-based wait racing an independent dispatcher would be exactly the kind
    // of async flake this project already has one documented case of (ScreenTracker/stop()) —
    // not worth reproducing for a 3-line, visually-verifiable delegation. The dedup/recording
    // logic itself (the part with real branching) is covered deterministically above via
    // ExperimentTracker directly.
}
