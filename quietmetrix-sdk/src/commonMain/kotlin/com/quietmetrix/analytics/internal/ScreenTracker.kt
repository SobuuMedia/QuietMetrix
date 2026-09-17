package com.quietmetrix.analytics.internal

import com.quietmetrix.analytics.internal.ScreenTracker.enter
import com.quietmetrix.analytics.internal.ScreenTracker.flush
import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.internal.counters.dwellBucket
import com.quietmetrix.analytics.trackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Tracks how long the user stays on each screen, and — the counter-native half of this — the
 * device's own record of the screen flow: A -> B transitions and per-screen dwell buckets,
 * recorded via [MetricGateway] rather than sent as a reconstructable per-session trail.
 *
 * Screens are reported manually via [com.quietmetrix.analytics.trackScreen]; the SDK measures the
 * dwell time itself. A `screen_view` event carrying `duration_ms` in its props is emitted when the
 * user leaves a screen — either by entering another one ([enter]) or when the current screen is
 * closed out ([flush], used on app background / shutdown).
 */
@OptIn(ExperimentalTime::class)
internal object ScreenTracker {
    const val SCREEN_VIEW_EVENT = "screen_view"
    const val DURATION_PROP = "duration_ms"

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentScreen: String? = null
    private var enteredAt: Instant? = null
    private var currentProps: Map<String, Any?> = emptyMap()

    /**
     * Closes out the current screen (if any) and starts timing [screen]. Records a
     * `screen_transition{from, to}` counter when there was a previous screen and it differs
     * from [screen] — regardless of dwell time, since an instant bounce is itself a signal
     * (unlike the `screen_dwell`/`screen_view` emission below, which needs positive dwell).
     */
    suspend fun enter(screen: String, props: Map<String, Any?> = emptyMap(), now: Instant = Clock.System.now()) {
        val previousScreen = flush(now)
        mutex.withLock {
            currentScreen = screen
            enteredAt = now
            currentProps = props
        }
        if (previousScreen != null && previousScreen != screen) {
            MetricGateway.record("screen_transition", mapOf("from" to previousScreen, "to" to screen), now = now)
        }
    }

    /**
     * Emits a `screen_view` event and a `screen_dwell` counter for the current screen (if its
     * elapsed time is positive), then clears it. Returns the screen that was active, or null
     * if none was — used by [enter] to build a transition even when dwell was zero.
     */
    suspend fun flush(now: Instant = Clock.System.now()): String? {
        val screen: String?
        val entered: Instant?
        val props: Map<String, Any?>
        mutex.withLock {
            screen = currentScreen
            entered = enteredAt
            props = currentProps
            currentScreen = null
            enteredAt = null
            currentProps = emptyMap()
        }
        if (screen == null || entered == null) return null
        val durationMs = (now - entered).inWholeMilliseconds
        if (durationMs > 0) {
            MetricGateway.record("screen_dwell", mapOf("screen" to screen, "bucket" to dwellBucket(durationMs)), now = now)
            trackEvent(SCREEN_VIEW_EVENT, screen, props + (DURATION_PROP to durationMs))
        }
        return screen
    }

    /**
     * Fire-and-forget close-out for non-suspending callers (lifecycle callbacks, QuietMetrix.stop).
     * Emits the current screen's dwell time, if any.
     */
    fun closeOutAsync() {
        scope.launch { flush() }
    }

    /**
     * Running dwell (ms) on the current screen at [now], or null if no screen
     * is active or the elapsed time is not positive. Used to stamp time-on-screen
     * onto every event, not just `screen_view`.
     */
    suspend fun currentDwellMs(now: Instant = Clock.System.now()): Long? {
        val entered = mutex.withLock { enteredAt }
        if (entered == null) return null
        val ms = (now - entered).inWholeMilliseconds
        return if (ms > 0) ms else null
    }

    /**
     * Returns [props] with a `duration_ms` entry for the current screen's running
     * dwell, when no `duration_ms` is already present and a screen is active.
     * Existing `duration_ms` values (e.g. the final dwell set by [flush] on a
     * `screen_view` event, or a caller-supplied value) are preserved unchanged.
     */
    suspend fun enrichWithDwell(
        props: Map<String, Any?>,
        now: Instant = Clock.System.now(),
    ): Map<String, Any?> {
        if (DURATION_PROP in props) return props
        val ms = currentDwellMs(now) ?: return props
        return props + (DURATION_PROP to ms)
    }

    /** The screen currently being tracked, or null if none is active. Used to default
     *  [com.quietmetrix.analytics.trackSearch]'s `screen` argument when the caller omits it. */
    internal suspend fun currentScreenName(): String? = mutex.withLock { currentScreen }

    internal suspend fun reset() {
        mutex.withLock {
            currentScreen = null
            enteredAt = null
            currentProps = emptyMap()
        }
    }
}
