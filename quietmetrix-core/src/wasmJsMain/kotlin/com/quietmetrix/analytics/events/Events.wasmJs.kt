package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder

actual fun trackEvent(event: String, screen: String?) {
    if (!ConfigHolder.isInitialized) return
    if (!isTrackingAllowed()) return
    callQuietmetrixTrack(event, screen)
}

private fun callQuietmetrixTrack(e: String, s: String?): Unit =
    js("(typeof window.__quietmetrixTrack==='function') && window.__quietmetrixTrack(e,s)")
