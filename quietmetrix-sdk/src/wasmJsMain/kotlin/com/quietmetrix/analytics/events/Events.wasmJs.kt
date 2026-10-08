package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.EventValidator
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.counters.recordEventCounter

internal actual suspend fun platformTrackEvent(event: String, screen: String?, props: Map<String, Any?>) {
    if (!Gate.shouldTrack() || EventValidator.validate(event, screen, props).isNotEmpty()) return
    recordEventCounter(event, screen, props)
}
