package com.quietmetrix.analytics.js

import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.hasCookieConsent as coreHasCookieConsent
import com.quietmetrix.analytics.isTrackingAllowed as coreIsTrackingAllowed
import com.quietmetrix.analytics.setCookieConsent as coreSetCookieConsent
import com.quietmetrix.analytics.trackEvent as coreTrackEvent
import com.quietmetrix.analytics.trackScreen as coreTrackScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.promise
import kotlin.js.Promise

/**
 * JavaScript/TypeScript entry point for the QuietMetrix SDK.
 *
 * The core SDK is built on Kotlin `suspend` functions, which cannot be exported to JS. This
 * facade adapts them into a plain JS API: suspend calls become `Promise`-returning functions,
 * and configuration is taken as a typed options object. These are the symbols published to the
 * `@quietmetrix/sdk` npm package (e.g. `import { init, trackEvent } from "@quietmetrix/sdk"`).
 */

private val facadeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Options accepted by [init]. Mirrors the fields of the Kotlin `QuietMetrixConfig`. */
@JsExport
external interface QuietMetrixInitOptions {
    var storageKeyPrefix: String
    var trackingEndpoint: String?
    var apiKey: String?
    var flushIntervalMs: Int?
    var maxQueueSize: Int?
    var autoTrackInitialPageView: Boolean?
    var trackingAllowedByDefault: Boolean?
    var userAgent: String?
    var debug: Boolean?
}

/** Optional second argument to [trackEvent]. */
@JsExport
external interface TrackOptions {
    var screen: String?
    var props: Any?
}

/** Initialize the SDK. Call once, before any tracking. */
@JsExport
fun init(options: QuietMetrixInitOptions) {
    QuietMetrix.init(
        QuietMetrixConfig(
            storageKeyPrefix = options.storageKeyPrefix,
            trackingEndpoint = options.trackingEndpoint,
            apiKey = options.apiKey,
            flushIntervalMs = (options.flushIntervalMs ?: 30_000).toLong(),
            maxQueueSize = options.maxQueueSize ?: 1000,
            autoTrackInitialPageView = options.autoTrackInitialPageView ?: true,
            trackingAllowedByDefault = options.trackingAllowedByDefault ?: false,
            userAgent = options.userAgent,
            debug = options.debug ?: false,
        )
    )
}

/** Track a named event. `options` may carry a `screen` and a plain `props` object. */
@JsExport
fun trackEvent(event: String, options: TrackOptions? = null): Promise<Unit> = facadeScope.promise {
    coreTrackEvent(event, options?.screen, jsObjectToMap(options?.props))
}

/** Track a screen/route view (records dwell time until the next screen). */
@JsExport
fun trackScreen(screen: String, props: Any? = null): Promise<Unit> = facadeScope.promise {
    coreTrackScreen(screen, jsObjectToMap(props))
}

/** Associate subsequent events with a user id (hashed before it leaves the device). Pass null to clear. */
@JsExport
fun identify(userId: String?) {
    QuietMetrix.identify(userId)
}

/** Flush any queued events now. Useful on `beforeunload`. */
@JsExport
fun flush(): Promise<Unit> = facadeScope.promise {
    QuietMetrix.flush()
}

/** Persist the user's cookie-consent choice. */
@JsExport
fun setCookieConsent(accepted: Boolean) {
    coreSetCookieConsent(accepted)
}

/** True once the user has made a cookie-consent choice. */
@JsExport
fun hasCookieConsent(): Boolean = coreHasCookieConsent()

/** True when events are currently allowed to be sent. */
@JsExport
fun isTrackingAllowed(): Boolean = coreIsTrackingAllowed()

/** Master kill switch, independent of cookie consent. */
@JsExport
fun setAnalyticsEnabled(enabled: Boolean) {
    QuietMetrix.setAnalyticsEnabled(enabled)
}

/** Stop background flushing and screen tracking. */
@JsExport
fun stop() {
    QuietMetrix.stop()
}

private fun jsObjectToMap(obj: Any?): Map<String, Any?> {
    if (obj == null) return emptyMap()
    val result = LinkedHashMap<String, Any?>()
    val keys: Array<String> = js("Object.keys(obj)")
    for (k in keys) {
        result[k] = js("obj[k]")
    }
    return result
}
