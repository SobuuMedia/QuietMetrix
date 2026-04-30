package com.quietmetrix.analytics

import android.util.Log
import com.quietmetrix.analytics.internal.ConfigHolder

actual fun trackEvent(event: String, screen: String?) {
    if (!ConfigHolder.isInitialized) return
    if (!isTrackingAllowed()) return
    Log.d("QuietMetrix", "event=$event screen=${screen ?: "-"}")
}
