package com.quietmetrix.analytics

/**
 * Configuration for QuietMetrix. Pass once to [QuietMetrix.init] at app startup.
 *
 * @param storageKeyPrefix Prefix for all persistent storage keys (localStorage on web,
 *   SharedPreferences on Android, NSUserDefaults on iOS, in-memory on JVM). Pick a unique
 *   value per app (e.g. "myapp_") so multiple consumers on the same origin do not collide.
 * @param trackingEndpoint URL that receives POSTed events. Used by the wasmJs target only.
 *   Null disables network sending; events still respect consent and run consent/banner logic.
 * @param autoTrackInitialPageView If true, the wasmJs tracker fires a single `page_view`
 *   event on init. Set to false if you'd rather emit it manually.
 * @param trackingAllowedByDefault If the user has not yet made a consent choice, should
 *   events fire? Default true preserves the privacy posture of "anonymous analytics until
 *   declined". Set to false for stricter opt-in semantics.
 */
data class QuietMetrixConfig(
    val storageKeyPrefix: String,
    val trackingEndpoint: String? = null,
    val apiKey: String? = null,
    val flushIntervalMs: Long = 30_000L,
    val maxQueueSize: Int = 1000,
    val autoTrackInitialPageView: Boolean = true,
    val trackingAllowedByDefault: Boolean = false,
    val userAgent: String? = null,
    val applicationContext: Any? = null,
    val debug: Boolean = false,
)
