package com.quietmetrix.analytics.internal.funnels

import com.quietmetrix.analytics.Funnel
import com.quietmetrix.analytics.FunnelCountMode
import com.quietmetrix.analytics.FunnelManifest
import com.quietmetrix.analytics.FunnelStep
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.internal.counters.MetricRecorder
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [FunnelEvaluator] is the on-device replacement for server-side funnel matching
 * (FunnelAnalyzer.kt / funnelAnalyze.php): the device tracks its own progress through a
 * funnel's declared steps in real time and reports only how deep it got, as
 * `funnel_step{f, rev, step}` counters — never the underlying event trail.
 *
 * Each test's `storageKeyPrefix` gets a random per-instance suffix: the JVM PersistentStore
 * backing ACTOR-mode progress writes real files under ~/.quietmetrix (see
 * FileBasedPersistentStore), so a fixed prefix would leak state not just across tests in one
 * run but across separate test runs entirely (progress is designed to survive exactly that) —
 * a fixed "ft7_" collided with its own prior run's leftover file the first time this suite ran
 * twice. IdentityUtilsTest gets away with fixed prefixes only because its persisted value is
 * checked for shape, not for starting from a clean slate.
 */
@OptIn(ExperimentalTime::class)
class FunnelEvaluatorTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000_000_000)
    private val runId = Random.nextInt()

    @AfterTest
    fun tearDown() {
        FunnelEvaluator.reset()
        MetricGateway.reset()
    }

    private fun twoStepFunnel(
        key: String,
        windowSeconds: Long = 3600L,
        countMode: FunnelCountMode = FunnelCountMode.ACTOR,
        correlationProperty: String? = null,
    ) = Funnel(
        key = key,
        name = "Signup",
        steps = listOf(
            FunnelStep(key = "view", event = "screen_view", screen = "signup"),
            FunnelStep(key = "submit", event = "signup_submitted"),
        ),
        windowSeconds = windowSeconds,
        countMode = countMode,
        correlationProperty = correlationProperty,
    )

    private fun configure(funnel: Funnel, prefix: String, revision: Long = 1L) {
        FunnelEvaluator.configure(
            QuietMetrixConfig(
                storageKeyPrefix = "${prefix}${runId}_",
                funnelManifest = FunnelManifest(namespace = "app", revision = revision, funnels = listOf(funnel)),
            ),
        )
    }

    private suspend fun pending() = MetricGateway.drain()
    private fun List<MetricRecorder.PendingCounter>.funnelSteps() = filter { it.metric == "funnel_step" }

    @Test
    fun `the first matching event for step 0 advances to step 1`() = runTest {
        val funnel = twoStepFunnel("signup_ft1")
        configure(funnel, "ft1_")

        FunnelEvaluator.onEvent("screen_view", "signup", emptyMap(), now = t0)

        val steps = pending().funnelSteps()
        assertEquals(1, steps.size)
        assertEquals(mapOf("f" to "signup_ft1", "rev" to "1", "step" to "1"), steps[0].dims)
    }

    @Test
    fun `an unrelated event does not advance the funnel`() = runTest {
        val funnel = twoStepFunnel("signup_ft2")
        configure(funnel, "ft2_")

        FunnelEvaluator.onEvent("button_click", "home", emptyMap(), now = t0)

        assertTrue(pending().funnelSteps().isEmpty())
    }

    @Test
    fun `reaching step 2 out of order (before step 1) does not advance`() = runTest {
        val funnel = twoStepFunnel("signup_ft3")
        configure(funnel, "ft3_")

        FunnelEvaluator.onEvent("signup_submitted", null, emptyMap(), now = t0)

        assertTrue(pending().funnelSteps().isEmpty())
    }

    @Test
    fun `reaching both steps in order records one counter per depth reached`() = runTest {
        val funnel = twoStepFunnel("signup_ft4")
        configure(funnel, "ft4_")

        FunnelEvaluator.onEvent("screen_view", "signup", emptyMap(), now = t0)
        FunnelEvaluator.onEvent("signup_submitted", null, emptyMap(), now = t0.plus(10.seconds))

        val steps = pending().funnelSteps().sortedBy { it.dims.getValue("step") }
        assertEquals(2, steps.size)
        assertEquals("1", steps[0].dims["step"])
        assertEquals("2", steps[1].dims["step"])
    }

    @Test
    fun `completing a funnel is idempotent - a further matching event records nothing more`() = runTest {
        val funnel = twoStepFunnel("signup_ft5")
        configure(funnel, "ft5_")

        FunnelEvaluator.onEvent("screen_view", "signup", emptyMap(), now = t0)
        FunnelEvaluator.onEvent("signup_submitted", null, emptyMap(), now = t0.plus(1.seconds))
        pending() // drain the two step counters

        FunnelEvaluator.onEvent("signup_submitted", null, emptyMap(), now = t0.plus(2.seconds))
        assertTrue(pending().funnelSteps().isEmpty())
    }

    @Test
    fun `an event arriving after the window deadline does not advance the funnel`() = runTest {
        val funnel = twoStepFunnel("signup_ft6", windowSeconds = 60L)
        configure(funnel, "ft6_")

        FunnelEvaluator.onEvent("screen_view", "signup", emptyMap(), now = t0)
        pending()

        FunnelEvaluator.onEvent("signup_submitted", null, emptyMap(), now = t0.plus(120.seconds))
        assertTrue(pending().funnelSteps().isEmpty())
    }

    @Test
    fun `progress survives a fresh configure with the same prefix (persisted across restarts)`() = runTest {
        val funnel = twoStepFunnel("signup_ft7")
        configure(funnel, "ft7_")
        FunnelEvaluator.onEvent("screen_view", "signup", emptyMap(), now = t0)
        pending()

        // Simulate a process restart: in-memory state is gone, only the persistent store remains.
        FunnelEvaluator.reset()
        configure(funnel, "ft7_")

        FunnelEvaluator.onEvent("signup_submitted", null, emptyMap(), now = t0.plus(5.seconds))
        val steps = pending().funnelSteps()
        assertEquals(1, steps.size)
        assertEquals("2", steps[0].dims["step"])
    }

    @Test
    fun `ATTEMPT mode tracks two correlated attempts independently`() = runTest {
        val funnel = twoStepFunnel("checkout_ft8", countMode = FunnelCountMode.ATTEMPT, correlationProperty = "cart_id")
        configure(funnel, "ft8_")

        FunnelEvaluator.onEvent("screen_view", "signup", mapOf("cart_id" to "A"), now = t0)
        FunnelEvaluator.onEvent("screen_view", "signup", mapOf("cart_id" to "B"), now = t0)
        FunnelEvaluator.onEvent("signup_submitted", null, mapOf("cart_id" to "A"), now = t0.plus(1.seconds))

        val steps = pending().funnelSteps()
        // A reached step 1 then step 2; B reached only step 1 -- two distinct cells (the
        // correlation id is never a dim, so both attempts reaching step 1 merge into one cell
        // with n=2, exactly the aggregation that keeps this counter privacy-preserving).
        assertEquals(2, steps.size)
        val byStep = steps.associateBy { it.dims["step"] }
        assertEquals(2L, byStep.getValue("1").n)
        assertEquals(1L, byStep.getValue("2").n)
    }

    @Test
    fun `ATTEMPT mode ignores an event that lacks the correlation property`() = runTest {
        val funnel = twoStepFunnel("checkout_ft9", countMode = FunnelCountMode.ATTEMPT, correlationProperty = "cart_id")
        configure(funnel, "ft9_")

        FunnelEvaluator.onEvent("screen_view", "signup", emptyMap(), now = t0)

        assertTrue(pending().funnelSteps().isEmpty())
    }

    @Test
    fun `a funnel with no steps is a safe no-op`() = runTest {
        val funnel = Funnel(key = "empty_ft10", name = "Empty", steps = emptyList())
        configure(funnel, "ft10_")

        FunnelEvaluator.onEvent("anything", null, emptyMap(), now = t0)

        assertTrue(pending().funnelSteps().isEmpty())
    }

    @Test
    fun `no manifest configured is a safe no-op`() = runTest {
        FunnelEvaluator.onEvent("screen_view", "signup", emptyMap(), now = t0)
        assertTrue(pending().funnelSteps().isEmpty())
    }
}
