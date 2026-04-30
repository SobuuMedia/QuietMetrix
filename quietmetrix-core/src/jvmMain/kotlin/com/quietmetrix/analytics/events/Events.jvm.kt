package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder

actual fun trackEvent(event: String, screen: String?) {
    if (!ConfigHolder.isInitialized) return
    if (!isTrackingAllowed()) return
    println("[QuietMetrix] event=$event screen=${screen ?: "-"}")
}
