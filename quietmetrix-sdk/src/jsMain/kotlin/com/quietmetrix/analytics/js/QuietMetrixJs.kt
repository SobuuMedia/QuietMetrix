package com.quietmetrix.analytics.js

import com.quietmetrix.analytics.Funnel
import com.quietmetrix.analytics.FunnelStep
import com.quietmetrix.analytics.QuietMetrix
import com.quietmetrix.analytics.QuietMetrixConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.promise
import kotlin.js.Promise
import com.quietmetrix.analytics.hasCookieConsent as coreHasCookieConsent
import com.quietmetrix.analytics.isTrackingAllowed as coreIsTrackingAllowed
import com.quietmetrix.analytics.setCookieConsent as coreSetCookieConsent
import com.quietmetrix.analytics.trackEvent as coreTrackEvent
import com.quietmetrix.analytics.trackScreen as coreTrackScreen

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
    var autoTrackInitialPageView: Boolean?
    var trackingAllowedByDefault: Boolean?
    var userAgent: String?
    var debug: Boolean?
    var funnels: Array<FunnelOptions>?
}

/** One step of a [FunnelOptions]. Mirrors the Kotlin `FunnelStep`. */
@JsExport
external interface FunnelStepOptions {
    var key: String
    var event: String
    var name: String?
    var screen: String?
    var props: Any?
}

/** A funnel declared from JS/TypeScript. Pass via `QuietMetrixInitOptions.funnels` or [defineFunnel]. */
@JsExport
external interface FunnelOptions {
    var key: String
    var name: String
    var description: String?
    var steps: Array<FunnelStepOptions>
    var windowSeconds: Int?
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
            autoTrackInitialPageView = options.autoTrackInitialPageView ?: true,
            trackingAllowedByDefault = options.trackingAllowedByDefault ?: false,
            userAgent = options.userAgent,
            debug = options.debug ?: false,
            funnels = (options.funnels ?: emptyArray()).map { toFunnel(it) },
        )
    )
}

/**
 * Declares a funnel and returns a handle whose [FunnelHandle.step] emits a step's event.
 * Prefer passing funnels via `QuietMetrixInitOptions.funnels` so they are auto-registered on
 * [init] — this is for apps that want a handle to call `.step()` without re-declaring the
 * funnel at every call site.
 */
@JsExport
fun defineFunnel(options: FunnelOptions): FunnelHandle = FunnelHandle(toFunnel(options))

/** Returned by [defineFunnel]. Not constructed directly. */
@JsExport
class FunnelHandle internal constructor(private val funnel: Funnel) {
    /** Emits [funnel]'s declared event for [stepKey], merging in [props]. No-op for an undeclared key. */
    fun step(stepKey: String, props: Any? = null): Promise<Unit> = facadeScope.promise {
        funnel.step(stepKey, jsObjectToMap(props))
    }
}

private fun toFunnel(options: FunnelOptions): Funnel = Funnel(
    key = options.key,
    name = options.name,
    description = options.description,
    windowSeconds = (options.windowSeconds ?: (7 * 24 * 3600)).toLong(),
    steps = options.steps.map { s ->
        FunnelStep(
            key = s.key,
            event = s.event,
            name = s.name,
            screen = s.screen,
            props = jsObjectToMap(s.props).mapValues { it.value?.toString() ?: "" },
        )
    },
)

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
