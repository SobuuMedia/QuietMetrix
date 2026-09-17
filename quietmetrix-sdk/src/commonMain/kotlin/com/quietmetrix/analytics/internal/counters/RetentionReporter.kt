package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.createPersistentStore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Retention without a session trail: the device persists its own first-launch date once, and
 * on each subsequent call (from [com.quietmetrix.analytics.QuietMetrix.init], i.e. roughly
 * once per app launch) checks which of the tracked day-N marks have now been reached *or
 * passed* since that date — standard "still active at least N days later" retention
 * semantics, not "active on exactly day N": a device opened on day 8, not day 7, still counts
 * for the day-7 mark. Each mark still fires at most once ever, so a device that goes quiet for
 * 40 days and comes back reports day 1, 3, 7, 14, *and* 30 in that one call — it genuinely
 * satisfies all five. The device already did this arithmetic itself; the server never sees a
 * session list to reconstruct a cohort from.
 *
 * `day="0"` is a special mark: it fires exactly once, on the very first call (the moment the
 * first-launch date is set), and is the cohort-size signal. Without it, a server reading
 * `retention{cohort, day="7"}` would have a numerator (devices that returned) but no
 * denominator (devices that joined the cohort at all) — day-N retention is a percentage, and
 * a percentage needs both.
 *
 * A suspend function, not fire-and-forget internally — the caller (`QuietMetrix.init`, which
 * isn't suspend) wraps it in its own scope launch, the same way it already does for
 * [CounterFlusher.stop]'s final flush. Kept this way so it stays directly testable.
 */
@OptIn(ExperimentalTime::class)
internal object RetentionReporter {

    private val dayBuckets = listOf(1L, 3L, 7L, 14L, 30L)

    suspend fun reportIfDue(config: QuietMetrixConfig, now: Instant = Clock.System.now()) {
        val store = createPersistentStore(config.storageKeyPrefix)
        val today = utcTodayIso(now)

        val firstLaunch = ensureFirstLaunchDay(store, today)
        val firstLaunchDay = firstLaunch.firstLaunchDay
        if (firstLaunch.isFirstCall) {
            val cohort = weekCohort(epochDayFromIso(firstLaunchDay))
            MetricGateway.record("retention", mapOf("cohort" to cohort, "day" to "0"), now = now)
        }

        val daysSince = epochDayFromIso(today) - epochDayFromIso(firstLaunchDay)
        val cohort = weekCohort(epochDayFromIso(firstLaunchDay))
        for (bucket in dayBuckets) {
            if (daysSince < bucket) continue
            val reportedKey = "retention_reported_day$bucket"
            if (store.get(reportedKey) == "1") continue
            store.set(reportedKey, "1")
            MetricGateway.record("retention", mapOf("cohort" to cohort, "day" to bucket.toString()), now = now)
        }
    }
}
