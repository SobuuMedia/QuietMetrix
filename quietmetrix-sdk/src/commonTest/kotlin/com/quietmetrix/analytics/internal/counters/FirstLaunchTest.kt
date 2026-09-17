package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.createPersistentStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ensureFirstLaunchDay] is the single source of truth for "days since install" shared by
 * [RetentionReporter] and [ActivationReporter] — both must agree on the same first-launch
 * date, or a device's retention cohort and activation cohort could silently disagree.
 *
 * Uses the real JVM [createPersistentStore] (file-backed under ~/.quietmetrix) with a random
 * per-instance prefix, same reasoning as RetentionReporterTest: a fixed prefix would leak
 * state across separate test runs.
 */
class FirstLaunchTest {

    private val runId = Random.nextInt()
    private fun store(name: String) = createPersistentStore("fl${runId}_$name")

    @Test
    fun `the first-ever call sets and returns today, marked as the first call`() {
        val result = ensureFirstLaunchDay(store("a"), today = "2026-09-04")
        assertEquals("2026-09-04", result.firstLaunchDay)
        assertTrue(result.isFirstCall)
    }

    @Test
    fun `a later call returns the original day, not marked as the first call`() {
        val s = store("b")
        ensureFirstLaunchDay(s, today = "2026-09-04")

        val result = ensureFirstLaunchDay(s, today = "2026-09-10")
        assertEquals("2026-09-04", result.firstLaunchDay)
        assertFalse(result.isFirstCall)
    }

    @Test
    fun `independent stores track independent first-launch dates`() {
        val a = ensureFirstLaunchDay(store("c1"), today = "2026-09-04")
        val b = ensureFirstLaunchDay(store("c2"), today = "2026-09-10")
        assertEquals("2026-09-04", a.firstLaunchDay)
        assertEquals("2026-09-10", b.firstLaunchDay)
    }
}
