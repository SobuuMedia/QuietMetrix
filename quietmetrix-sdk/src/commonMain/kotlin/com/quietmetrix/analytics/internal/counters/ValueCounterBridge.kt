package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.EventValidator

/**
 * The counter-native backing for [com.quietmetrix.analytics.trackValue]: records a `value{name}`
 * counter whose `n` is the reported amount itself (in integer minor units, e.g. cents) rather
 * than an occurrence count — the same additive-counter mechanism every other metric uses,
 * `MetricRecorder.record`'s `n` parameter already sums arbitrary deltas. Negative amounts are
 * accepted (refunds/adjustments); only the name is validated.
 */
internal suspend fun recordValueCounter(
    name: String,
    amountMinorUnits: Long,
    debug: Boolean = com.quietmetrix.analytics.internal.ConfigHolder.configOrNull?.debug == true,
) {
    val errors = EventValidator.validate(name, screen = null, props = emptyMap())
    if (errors.isNotEmpty()) {
        if (debug) println("[QuietMetrix] trackValue(\"$name\") rejected: ${errors.joinToString("; ")}")
        return
    }
    MetricGateway.record("value", mapOf("name" to name), n = amountMinorUnits)
}
