package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import platform.Foundation.NSLog

actual fun trackEvent(event: String, screen: String?) {
    if (!ConfigHolder.isInitialized) return
    if (!isTrackingAllowed()) return
    NSLog("[QuietMetrix] event=%@ screen=%@", event, screen ?: "-")
}
