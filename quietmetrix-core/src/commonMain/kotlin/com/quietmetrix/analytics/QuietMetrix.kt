package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder

/** Entry point for the QuietMetrix analytics SDK. */
object QuietMetrix {
    /**
     * Initialize the SDK. Call once at app startup before any [trackEvent] or consent calls.
     * Idempotent — a second call replaces the configuration.
     */
    fun init(config: QuietMetrixConfig) {
        ConfigHolder.set(config)
        platformInit(config)
    }

    /** True once [init] has been called. */
    val isInitialized: Boolean get() = ConfigHolder.isInitialized
}

internal expect fun platformInit(config: QuietMetrixConfig)
