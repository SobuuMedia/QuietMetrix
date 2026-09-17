package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.createPersistentStore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Reports a device as "activated" — [QuietMetrixConfig.activationEvent] fired within
 * [QuietMetrixConfig.activationWindowDays] of first launch — as an `activation{cohort}`
 * counter. Shares [ensureFirstLaunchDay] with [RetentionReporter] so the two never disagree
 * about a device's first-launch date; `cohort` uses the identical `weekCohort()` label, which
 * lets a dashboard compute an activation *rate* by joining against retention's `day="0"`
 * cohort-size signal for the same cohort key.
 *
 * Unlike retention's day-N marks — deliberately an "at least N days later" signal — activation
 * is a hard window: firing after [QuietMetrixConfig.activationWindowDays] has passed never
 * records anything. A device either activated in time or it didn't.
 *
 * Called from [com.quietmetrix.analytics.internal.counters.recordEventCounter] on every
 * `trackEvent` call, the same dispatch point [com.quietmetrix.analytics.internal.funnels
 * .FunnelEvaluator] already uses — cheap to call unconditionally since it no-ops immediately
 * when [QuietMetrixConfig.activationEvent] is null or the event name doesn't match.
 */
@OptIn(ExperimentalTime::class)
internal object ActivationReporter {

    private const val SETTLED_KEY = "activation_settled"

    suspend fun onEvent(config: QuietMetrixConfig, event: String, now: Instant = Clock.System.now()) {
        val activationEvent = config.activationEvent ?: return
        if (event != activationEvent) return

        val store = createPersistentStore(config.storageKeyPrefix)
        if (store.get(SETTLED_KEY) == "1") return

        val today = utcTodayIso(now)
        val firstLaunch = ensureFirstLaunchDay(store, today)
        // Settled either way: whether this reports activation or the window has already
        // closed, the device's outcome is now fixed and this reporter has nothing left to do.
        store.set(SETTLED_KEY, "1")

        val daysSince = epochDayFromIso(today) - epochDayFromIso(firstLaunch.firstLaunchDay)
        if (daysSince > config.activationWindowDays) return

        val cohort = weekCohort(epochDayFromIso(firstLaunch.firstLaunchDay))
        MetricGateway.record("activation", mapOf("cohort" to cohort), now = now)
    }
}
