package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.EventValidator
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.counters.recordEventCounter

/**
 * Validates [event]/[screen]/[props], then records an `event{name}` counter — see
 * [recordEventCounter] for why [props] and [screen] never reach the wire.
 */
actual suspend fun trackEvent(event: String, screen: String?, props: Map<String, Any?>) {
    if (!Gate.shouldTrack()) return
    val errors = EventValidator.validate(event, screen, props)
    if (errors.isNotEmpty()) return
    recordEventCounter(event, screen, props)
}
