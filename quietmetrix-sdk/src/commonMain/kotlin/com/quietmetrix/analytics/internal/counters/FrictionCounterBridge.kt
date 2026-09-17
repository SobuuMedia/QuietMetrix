package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.ScreenTracker

/**
 * The single process-wide [RageTapDetector] every platform's tap-capture actual feeds into —
 * Android's `Window.Callback` wrapper (fully automatic, wired from the existing
 * `ActivityLifecycleCallbacks` hook) and iOS's `QuietMetrixWindow` (opt-in: the app must use it
 * in place of a plain `UIWindow`, since there is no safe automatic hook on iOS without
 * fragile method swizzling). JVM/Linux/Windows/Web have no tap-capture actual at all — an
 * honest platform gap, not a stub — so `friction` cells simply never appear from those
 * platforms.
 *
 * Tags the resulting `friction{screen, kind}` counter with [ScreenTracker]'s current screen
 * (`"unknown"` if none is active via `trackScreen`), the same way every other screen-scoped
 * counter here does. `kind` is fixed to `"rage_tap"` in this first version — the registry
 * declares it as an open value so a future `dead_tap`/`error_state` can be added without a
 * wire change.
 */
internal object FrictionCounterBridge {
    private var detector = RageTapDetector()

    suspend fun onTap(x: Float, y: Float, timestampMs: Long) {
        if (!detector.onTap(x, y, timestampMs)) return
        val screen = ScreenTracker.currentScreenName() ?: "unknown"
        MetricGateway.record("friction", mapOf("screen" to screen, "kind" to "rage_tap"))
    }

    /** Test-only: starts a fresh detector so tests don't leak burst state into one another. */
    internal fun reset() {
        detector = RageTapDetector()
    }
}
