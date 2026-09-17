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
 * [RetentionReporter] computes the device's own "I'm still active at least N days after first
 * launch" signal from a persisted first-launch date, and reports each mark once, ever, as a
 * `retention{cohort, day}` counter — the device knows its own return-visit pattern; the
 * server never reconstructs it from a session trail.
 *
 * `day="0"` is a special mark: it fires exactly once, on the device's first-ever call, and is
 * the cohort-size signal — without it, a server reading `retention{cohort, day="7"}` would
 * have a numerator (devices that returned) but no denominator (devices that joined the cohort
 * at all), making a day-7 *percentage* uncomputable.
 *
 * Semantics are "at least N days later," not "exactly on day N": a device opened on day 8, not
 * day 7, still counts for the day-7 mark, and a device that goes quiet for 40 days and comes
 * back reports day 1, 3, 7, 14, *and* 30 in that one call — it genuinely satisfies all five.
 *
 * Each test's `storageKeyPrefix` gets a random per-instance suffix — same reasoning as
 * FunnelEvaluatorTest: the JVM PersistentStore backing first-launch tracking writes real
 * files under ~/.quietmetrix, so a fixed prefix would leak state across separate test runs
 * (first-launch date, by design, is meant to survive exactly that).
 */
@OptIn(ExperimentalTime::class)
class RetentionReporterTest {

    private val t0 = Instant.fromEpochMilliseconds(epochDayFromCivil(2026, 9, 4) * 86_400_000L)
    private val runId = Random.nextInt()

    @AfterTest
    fun tearDown() {
        MetricGateway.reset()
    }

    private fun config(prefix: String) = QuietMetrixConfig(storageKeyPrefix = "${prefix}${runId}_")

    private suspend fun pending() = MetricGateway.drain()
    private fun List<MetricRecorder.PendingCounter>.retentionCells() = filter { it.metric == "retention" }
    private fun List<MetricRecorder.PendingCounter>.retentionDays() = retentionCells().mapNotNull { it.dims["day"] }.toSet()

    @Test
    fun `the first-ever call records the day-0 cohort-size signal`() = runTest {
        RetentionReporter.reportIfDue(config("rr1_"), now = t0)
        assertEquals(setOf("0"), pending().retentionDays())
    }

    @Test
    fun `a second call on the same day as first launch does not re-report day 0`() = runTest {
        val cfg = config("rr1b_")
        RetentionReporter.reportIfDue(cfg, now = t0)
        pending() // drain the day-0 report

        RetentionReporter.reportIfDue(cfg, now = t0)
        assertTrue(pending().retentionCells().isEmpty())
    }

    @Test
    fun `exactly one day later reports only the day-1 mark`() = runTest {
        val cfg = config("rr2_")
        RetentionReporter.reportIfDue(cfg, now = t0)
        pending() // drain the day-0 signal
        RetentionReporter.reportIfDue(cfg, now = t0.plus(1.days))

        assertEquals(setOf("1"), pending().retentionDays())
    }

    @Test
    fun `a day that overshoots the day-1 mark still reports it (at-least, not exactly)`() = runTest {
        val cfg = config("rr3_")
        RetentionReporter.reportIfDue(cfg, now = t0)
        pending() // drain the day-0 signal
        // Skips day 1 entirely, first returns on day 2 -- day-1 must still fire, since the
        // device has been active at least 1 day since first launch.
        RetentionReporter.reportIfDue(cfg, now = t0.plus(2.days))

        assertEquals(setOf("1"), pending().retentionDays())
    }

    @Test
    fun `a long gap reports every mark reached in one call`() = runTest {
        val cfg = config("rr4_")
        RetentionReporter.reportIfDue(cfg, now = t0)
        pending() // drain the day-0 signal
        RetentionReporter.reportIfDue(cfg, now = t0.plus(40.days))

        assertEquals(setOf("1", "3", "7", "14", "30"), pending().retentionDays())
    }

    @Test
    fun `a mark already reported is not reported again on a later call`() = runTest {
        val cfg = config("rr5_")
        RetentionReporter.reportIfDue(cfg, now = t0)
        pending()
        RetentionReporter.reportIfDue(cfg, now = t0.plus(1.days))
        pending() // day-1 reported here

        // Now day 3 arrives: day-1 must not repeat, only day-3 is new.
        RetentionReporter.reportIfDue(cfg, now = t0.plus(3.days))
        assertEquals(setOf("3"), pending().retentionDays())
    }

    @Test
    fun `the cohort label reflects the first-launch week, not the reporting day's week`() = runTest {
        val cfg = config("rr6_")
        RetentionReporter.reportIfDue(cfg, now = t0) // first launch: 2026-09-04, week 36
        pending()
        RetentionReporter.reportIfDue(cfg, now = t0.plus(30.days)) // reporting day is well into a later week

        val cell = pending().retentionCells().first { it.dims["day"] == "30" }
        assertEquals(weekCohort(epochDayFromCivil(2026, 9, 4)), cell.dims["cohort"])
    }

    @Test
    fun `a different storageKeyPrefix tracks its own independent first-launch date`() = runTest {
        val cfgA = config("rr7a_")
        val cfgB = config("rr7b_")
        RetentionReporter.reportIfDue(cfgA, now = t0)
        // B "first launches" a day later than A.
        RetentionReporter.reportIfDue(cfgB, now = t0.plus(1.days))
        pending() // drain both day-0 cohort-size signals (A's and B's)

        // One day after A's first launch is also the day B first launches (day 0 for B).
        RetentionReporter.reportIfDue(cfgA, now = t0.plus(1.days))
        RetentionReporter.reportIfDue(cfgB, now = t0.plus(1.days))

        val cells = pending().retentionCells()
        assertEquals(1, cells.size) // only A's day-1 bucket fires; B is still at day 0
    }
}
