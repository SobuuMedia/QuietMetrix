package com.quietmetrix.analytics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.Window
import com.quietmetrix.analytics.internal.ForegroundCounter
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.counters.FrictionCounterBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal actual fun platformInit(config: QuietMetrixConfig) {
    val ctx = config.applicationContext as? Context
    if (ctx != null) {
        registerForegroundCallbacks(ctx)
    }
}

/** Off the UI thread, dedicated to feeding tap samples into [FrictionCounterBridge] — touch
 *  dispatch must never block on a suspend call. */
private val frictionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

/**
 * Closes out the current screen's dwell time when the app moves to the background, so the final
 * screen the user was on is recorded even if they never navigate away. Also wraps each
 * Activity's [Window.Callback] to feed tap-down coordinates into [FrictionCounterBridge] for
 * automatic rage-tap detection — no app code required, unlike iOS's `QuietMetrixWindow`.
 */
private fun registerForegroundCallbacks(ctx: Context) {
    val app = ctx.applicationContext as? Application ?: return
    val counter = ForegroundCounter { ScreenTracker.closeOutAsync() }
    try {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = counter.onStart()
            override fun onActivityStopped(activity: Activity) = counter.onStop()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                wrapWindowCallbackForTapCapture(activity)
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    } catch (_: Exception) { }
}

private fun wrapWindowCallbackForTapCapture(activity: Activity) {
    try {
        val window = activity.window ?: return
        val original = window.callback ?: return
        // Already wrapped (shouldn't happen — onActivityCreated fires once per Activity — but
        // cheap to guard against double-wrapping if some framework quirk calls this twice).
        if (original is TapCapturingWindowCallback) return
        window.callback = TapCapturingWindowCallback(original)
    } catch (_: Exception) { }
}

/**
 * Delegates every [Window.Callback] method to [original] unchanged, intercepting only
 * [dispatchTouchEvent] to sample tap-down coordinates into [FrictionCounterBridge]. Never
 * consumes or alters the event — the app's own touch handling is completely unaffected.
 */
private class TapCapturingWindowCallback(private val original: Window.Callback) : Window.Callback by original {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.rawX
            val y = event.rawY
            val timestampMs = event.eventTime
            frictionScope.launch { FrictionCounterBridge.onTap(x, y, timestampMs) }
        }
        return original.dispatchTouchEvent(event)
    }
}
