package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.TRACKER_JS_TEMPLATE
import com.quietmetrix.analytics.internal.StorageKeys

internal actual fun platformInit(config: QuietMetrixConfig) {
    val endpoint = config.trackingEndpoint ?: return  // no endpoint → consent/banner still work, but no network tracker
    val consentKey = StorageKeys.cookieConsent(config.storageKeyPrefix)

    var script = TRACKER_JS_TEMPLATE
        .replace("__QM_ENDPOINT__", endpoint)
        .replace("__QM_CONSENT_KEY__", consentKey)

    if (!config.autoTrackInitialPageView) {
        // Strip the auto page-view line so init does not emit one.
        script = script.replace("send('page_view', null);", "")
    }

    injectScript(script)
}

private fun injectScript(scriptText: String): Unit =
    js("(function(t){var s=document.createElement('script');s.type='text/javascript';s.textContent=t;document.head.appendChild(s);})(scriptText)")
