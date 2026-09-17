package com.quietmetrix.analytics.internal.counters

/**
 * Detects a "rage tap" burst — [maxTaps] or more taps landing within [windowMs] of each other,
 * each within [radiusPx] of the previous one — from raw `(x, y, timestampMs)` samples. Pure
 * logic, no platform UI dependency: Android feeds it real touch-down coordinates via a
 * `Window.Callback` wrapper, iOS via a `UIWindow` subclass (`QuietMetrixWindow`), and it is
 * fully unit-testable without either.
 *
 * A tap farther than [radiusPx] from the previous one starts a fresh burst rather than
 * extending the current one — a rage tap is repeated jabbing at roughly the same spot, not any
 * three fast taps anywhere on screen. Firing clears the burst, so a new one can be detected
 * immediately after.
 */
internal class RageTapDetector(
    private val maxTaps: Int = 3,
    private val windowMs: Long = 1_500L,
    private val radiusPx: Float = 24f,
) {
    private data class Tap(val x: Float, val y: Float, val timestampMs: Long)

    private val burst = ArrayDeque<Tap>()

    /** Feed one tap-down sample. Returns true exactly when this tap completes a rage-tap
     *  burst (the [maxTaps]th tap within [windowMs] and [radiusPx] of the previous one). */
    fun onTap(x: Float, y: Float, timestampMs: Long): Boolean {
        while (burst.isNotEmpty() && timestampMs - burst.first().timestampMs > windowMs) {
            burst.removeFirst()
        }
        burst.lastOrNull()?.let { last ->
            val dx = x - last.x
            val dy = y - last.y
            if (dx * dx + dy * dy > radiusPx * radiusPx) {
                burst.clear()
            }
        }
        burst.addLast(Tap(x, y, timestampMs))
        if (burst.size >= maxTaps) {
            burst.clear()
            return true
        }
        return false
    }
}
