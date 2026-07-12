package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.EventValidator
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.SDK_VERSION
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.transport.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// All browser lookups guard on `typeof` so trackEvent works under SSR / plain Node without
// throwing; missing globals simply yield null context values.

private fun callQuietmetrixTrack(e: String, s: String?) {
    js("(typeof window !== 'undefined' && typeof window.__quietmetrixTrack === 'function') && window.__quietmetrixTrack(e, s)")
}

private fun generateSidNative(): String =
    js("Math.random().toString(36).slice(2) + Date.now().toString(36)")

private fun getDocumentReferrer(): String? =
    js("(typeof document !== 'undefined' && document.referrer) ? document.referrer : null")

private fun getNavigatorLanguage(): String? =
    js("(typeof navigator !== 'undefined' && navigator.language) ? navigator.language.split('-')[0] : null")

private fun getNavigatorUserAgent(): String? =
    js("(typeof navigator !== 'undefined' && navigator.userAgent) ? navigator.userAgent : null")

private fun getWindowViewport(): String? =
    js("(typeof window !== 'undefined') ? (window.innerWidth + 'x' + window.innerHeight) : null")

private fun getSessionSid(): String? =
    js("(typeof sessionStorage !== 'undefined') ? sessionStorage.getItem('_sid') : null")

@OptIn(ExperimentalTime::class)
actual suspend fun trackEvent(event: String, screen: String?, props: Map<String, Any?>) {
    if (!Gate.shouldTrack()) return
    val errors = EventValidator.validate(event, screen, props)
    if (errors.isNotEmpty()) return
    ConfigHolder.config
    val sid = getSessionSid() ?: generateSidNative()
    EventQueue.enqueue(
        EnqueuedEvent(
            event = event,
            screen = screen,
            props = ScreenTracker.enrichWithDwell(props),
            sid = sid,
            ts = Clock.System.now(),
            wasOffline = !ConnectivityMonitor().isOnline,
            sdk = SdkInfo("js", SDK_VERSION),
            ctx = EventContext(
                referrer = getDocumentReferrer(),
                language = getNavigatorLanguage(),
                ua = getNavigatorUserAgent(),
                viewport = getWindowViewport(),
            ),
        )
    )
    callQuietmetrixTrack(event, screen)
}
