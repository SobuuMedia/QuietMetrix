package com.quietmetrix.analytics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.quietmetrix.analytics.internal.ForegroundCounter
import com.quietmetrix.analytics.internal.ScreenTracker

internal actual fun platformInit(config: QuietMetrixConfig) {
    val ctx = config.applicationContext as? Context
    if (ctx != null) {
        com.quietmetrix.analytics.internal.transport.ConnectivityMonitor().let {
            try {
                it.initialize(ctx)
            } catch (_: Exception) { }
        }
        registerForegroundCallbacks(ctx)
    }
}

/**
 * Closes out the current screen's dwell time when the app moves to the background, so the final
 * screen the user was on is recorded even if they never navigate away.
 */
private fun registerForegroundCallbacks(ctx: Context) {
    val app = ctx.applicationContext as? Application ?: return
    val counter = ForegroundCounter { ScreenTracker.closeOutAsync() }
    try {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = counter.onStart()
            override fun onActivityStopped(activity: Activity) = counter.onStop()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    } catch (_: Exception) { }
}
