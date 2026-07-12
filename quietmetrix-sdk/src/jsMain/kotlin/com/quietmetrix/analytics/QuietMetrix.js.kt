package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.TRACKER_JS_TEMPLATE
import com.quietmetrix.analytics.internal.StorageKeys
import kotlinx.serialization.json.Json

internal actual fun platformInit(config: QuietMetrixConfig) {
    val endpoint = config.trackingEndpoint ?: return
    val consentKey = StorageKeys.cookieConsent(config.storageKeyPrefix)

    // Safely escape values before injecting into the JS template to prevent
    // script injection if endpoint or consentKey contain quotes/newlines.
    val safeEndpoint = Json.encodeToString(endpoint)
    val safeConsentKey = Json.encodeToString(consentKey)

    var script = TRACKER_JS_TEMPLATE
        .replace("'__QM_ENDPOINT__'", safeEndpoint)
        .replace("'__QM_CONSENT_KEY__'", safeConsentKey)

    if (!config.autoTrackInitialPageView) {
        script = script.replace("send('page_view', null);", "")
    }

    injectScript(script)
}

private fun injectScript(scriptText: String) {
    // Guarded so init() is a no-op (rather than a crash) under SSR / plain Node where
    // there is no document; the injected tracker only matters in a real browser anyway.
    js("if (typeof document !== 'undefined' && document.head) { (function(t){var s=document.createElement('script');s.type='text/javascript';s.textContent=t;document.head.appendChild(s);})(scriptText); }")
}
