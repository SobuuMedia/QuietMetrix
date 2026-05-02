package com.quietmetrix.analytics

import android.content.Context

internal actual fun platformInit(config: QuietMetrixConfig) {
    val ctx = config.applicationContext as? Context
    if (ctx != null) {
        com.quietmetrix.analytics.internal.transport.ConnectivityMonitor().let {
            try {
                it.initialize(ctx)
            } catch (_: Exception) { }
        }
    }
}
