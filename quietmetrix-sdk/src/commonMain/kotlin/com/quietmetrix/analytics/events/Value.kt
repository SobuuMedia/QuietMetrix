package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.counters.recordValueCounter

/**
 * Reports a numeric value under [name] — e.g. revenue from a purchase — rather than just an
 * occurrence count. [amountMinorUnits] is summed into a `value{name}` counter across every
 * device that reports it, in integer minor units (cents for USD) to avoid floating-point
 * drift; pass a negative amount for a refund or adjustment.
 *
 * Call this instead of [trackEvent] whenever the interesting number is an amount, not a count —
 * `trackValue("purchase", 499)` for a $4.99 purchase, not `trackEvent("purchase", props =
 * mapOf("amount" to 499))`, since the latter's `props` are never sent (see [trackEvent]).
 */
suspend fun trackValue(name: String, amountMinorUnits: Long) {
    if (!Gate.shouldTrack()) return
    recordValueCounter(name, amountMinorUnits)
}
