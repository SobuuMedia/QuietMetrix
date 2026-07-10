package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ScreenTracker

/**
 * Reports that the user navigated to [screen] and starts measuring time-on-screen.
 *
 * Call this on every screen change. The SDK records the dwell time of the *previous* screen and
 * emits a `screen_view` event for it (with a `duration_ms` property). The final screen is closed
 * out automatically when the app is backgrounded or the SDK is stopped.
 */
suspend fun trackScreen(screen: String, props: Map<String, Any?> = emptyMap()) {
    ScreenTracker.enter(screen, props)
}
