package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.EventValidator
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.SDK_VERSION
import com.quietmetrix.analytics.internal.generateSid
import com.quietmetrix.analytics.internal.transport.*
import kotlinx.datetime.Clock

@JsFun("(typeof window.__quietmetrixTrack==='function') && window.__quietmetrixTrack(e, s)")
external fun callQuietmetrixTrack(e: String, s: String?)

@JsFun("Math.random().toString(36).slice(2) + Date.now().toString(36)")
external fun generateSidNative(): String

@JsFun("document.referrer || null")
external fun getDocumentReferrer(): String?

@JsFun("navigator.language ? navigator.language.split('-')[0] : null")
external fun getNavigatorLanguage(): String?

@JsFun("navigator.userAgent || null")
external fun getNavigatorUserAgent(): String?

@JsFun("window.innerWidth + 'x' + window.innerHeight")
external fun getWindowViewport(): String

@JsFun("sessionStorage.getItem('_sid')")
external fun getSessionSid(): String?

actual suspend fun trackEvent(event: String, screen: String?, props: Map<String, Any?>) {
    if (!Gate.shouldTrack()) return
    val errors = EventValidator.validate(event, screen, props)
    if (errors.isNotEmpty()) return
    val config = ConfigHolder.config
    val sid = getSessionSid() ?: generateSidNative()
    EventQueue.enqueue(
        EnqueuedEvent(
            event = event,
            screen = screen,
            props = props,
            sid = sid,
            ts = Clock.System.now(),
            wasOffline = !ConnectivityMonitor().isOnline,
            sdk = SdkInfo("wasmJs", SDK_VERSION),
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
