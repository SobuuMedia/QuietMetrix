package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.PersistentStore

private const val FIRST_LAUNCH_KEY = "retention_first_launch_day"

/** [firstLaunchDay] is the persisted first-launch date (ISO yyyy-MM-dd); [isFirstCall] is true
 *  only on the call that just set it — i.e. this is genuinely the device's first-ever call
 *  into whichever reporter asked. */
internal data class FirstLaunchResult(val firstLaunchDay: String, val isFirstCall: Boolean)

/**
 * Gets-or-sets the device's first-launch date in [store], keyed the same way for every counter
 * producer that needs "days since install" — [RetentionReporter] and `ActivationReporter`
 * share this single persisted value rather than each tracking their own, so the two can never
 * disagree about when the device was first seen. The key name is retention's historical one
 * (`retention_first_launch_day`), kept as-is so an app already on a released SDK version
 * doesn't lose its first-launch date on upgrade.
 */
internal fun ensureFirstLaunchDay(store: PersistentStore, today: String): FirstLaunchResult {
    val existing = store.get(FIRST_LAUNCH_KEY)
    if (existing != null) return FirstLaunchResult(existing, isFirstCall = false)
    store.set(FIRST_LAUNCH_KEY, today)
    return FirstLaunchResult(today, isFirstCall = true)
}
