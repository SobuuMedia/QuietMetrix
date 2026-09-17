package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.EventValidator
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.counters.recordEventCounter

@JsFun("(typeof window.__quietmetrixTrack==='function') && window.__quietmetrixTrack(e, s)")
external fun callQuietmetrixTrack(e: String, s: String?)

/**
 * Validates [event]/[screen]/[props], then records an `event{name}` counter — see
 * [recordEventCounter] for why [props] and [screen] never reach the wire. Still notifies the
 * embedding page's `window.__quietmetrixTrack` hook, an unrelated local JS callback that never
 * sends data over the network.
 */
actual suspend fun trackEvent(event: String, screen: String?, props: Map<String, Any?>) {
    if (!Gate.shouldTrack()) return
    val errors = EventValidator.validate(event, screen, props)
    if (errors.isNotEmpty()) return
    recordEventCounter(event, screen, props)
    callQuietmetrixTrack(event, screen)
}
