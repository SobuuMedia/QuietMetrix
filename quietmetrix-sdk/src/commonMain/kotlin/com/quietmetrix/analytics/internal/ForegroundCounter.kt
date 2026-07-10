package com.quietmetrix.analytics.internal

/**
 * Tracks how many UI components (e.g. Android activities) are currently started, and invokes
 * [onBackground] when the count drops to zero — i.e. the app has moved to the background.
 *
 * Used by the Android lifecycle glue to close out the current screen's dwell time. The counter
 * never goes negative, so a stray "stopped" callback can't fire [onBackground] spuriously.
 */
internal class ForegroundCounter(private val onBackground: () -> Unit) {
    private var startedCount = 0

    fun onStart() {
        startedCount++
    }

    fun onStop() {
        if (startedCount == 0) return
        startedCount--
        if (startedCount == 0) onBackground()
    }
}
