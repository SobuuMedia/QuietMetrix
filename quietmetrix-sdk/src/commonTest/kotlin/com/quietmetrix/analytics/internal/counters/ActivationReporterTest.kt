package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.QuietMetrixConfig
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [ActivationReporter] reports a device "activated" — [QuietMetrixConfig.activationEvent] fired
 * within [QuietMetrixConfig.activationWindowDays] of first launch — as an `activation{cohort}`
 * counter, at most once per device, ever. Unlike retention's day-N marks, activation is not an
 * "at least" signal: firing after the window closes never counts.
 *
 * Random per-instance `storageKeyPrefix` suffix, same reasoning as RetentionReporterTest: the
 * JVM PersistentStore is file-backed under ~/.quietmetrix.
 */
@OptIn(ExperimentalTime::class)
class ActivationReporterTest {

    private val t0 = Instant.fromEpochMilliseconds(epochDayFromCivil(2026, 9, 4) * 86_400_000L)
    private val runId = Random.nextInt()

    @AfterTest
    fun tearDown() {
        MetricGateway.reset()
    }

    private fun config(prefix: String, activationEvent: String? = "onboarded", windowDays: Long = 3L) =
        QuietMetrixConfig(
            storageKeyPrefix = "${prefix}${runId}_",
            activationEvent = activationEvent,
            activationWindowDays = windowDays,
        )

    private suspend fun pending() = MetricGateway.drain()
    private fun List<MetricRecorder.PendingCounter>.activationCells() = filter { it.metric == "activation" }

    /** Establishes first-launch at [t0], the same way [RetentionReporter] always does at
     *  `QuietMetrix.init()` — before any `trackEvent` (and so any activation check) could
     *  plausibly happen. */
    private suspend fun withFirstLaunchAtT0(cfg: QuietMetrixConfig) {
        RetentionReporter.reportIfDue(cfg, now = t0)
        pending() // drain retention's day-0 signal; irrelevant to these tests
    }

    @Test
    fun `the configured event within the window records activation once`() = runTest {
        ActivationReporter.onEvent(config("ar1_"), "onboarded", now = t0)

        val cells = pending().activationCells()
        assertEquals(1, cells.size)
        assertEquals(setOf("cohort"), cells[0].dims.keys)
    }

    @Test
    fun `an unrelated event name is ignored`() = runTest {
        ActivationReporter.onEvent(config("ar2_"), "some_other_event", now = t0)
        assertTrue(pending().activationCells().isEmpty())
    }

    @Test
    fun `no activationEvent configured means nothing is ever recorded`() = runTest {
        ActivationReporter.onEvent(config("ar3_", activationEvent = null), "onboarded", now = t0)
        assertTrue(pending().activationCells().isEmpty())
    }

    @Test
    fun `firing exactly at the window boundary still counts`() = runTest {
        val cfg = config("ar4_", windowDays = 3L)
        withFirstLaunchAtT0(cfg)
        ActivationReporter.onEvent(cfg, "onboarded", now = t0.plus(3.days))
        assertEquals(1, pending().activationCells().size)
    }

    @Test
    fun `firing after the window closes never records activation`() = runTest {
        val cfg = config("ar5_", windowDays = 3L)
        withFirstLaunchAtT0(cfg)
        ActivationReporter.onEvent(cfg, "onboarded", now = t0.plus(4.days))
        assertTrue(pending().activationCells().isEmpty())
    }

    @Test
    fun `firing a second time within the window does not double-record`() = runTest {
        val cfg = config("ar6_")
        ActivationReporter.onEvent(cfg, "onboarded", now = t0)
        pending()
        ActivationReporter.onEvent(cfg, "onboarded", now = t0.plus(1.days))
        assertTrue(pending().activationCells().isEmpty())
    }

    @Test
    fun `the cohort label reflects the first-launch week`() = runTest {
        val cfg = config("ar7_")
        ActivationReporter.onEvent(cfg, "onboarded", now = t0)
        val cell = pending().activationCells().single()
        assertEquals(weekCohort(epochDayFromCivil(2026, 9, 4)), cell.dims["cohort"])
    }
}
