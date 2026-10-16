package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.Gate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Browser events share the consent-gated aggregate recorder; no raw tracker is injected.
private var removeBrowserLifecycle: (() -> Unit)? = null

internal actual fun platformInit(config: QuietMetrixConfig) {
    removeBrowserLifecycle?.invoke()
    removeBrowserLifecycle = null
    if (js("typeof document") != "undefined" && js("typeof window") != "undefined") {
        val document: dynamic = js("document")
        val window: dynamic = js("window")
        val visibility: (dynamic) -> Unit = {
            if (document.visibilityState == "hidden") QuietMetrix.onBackground() else QuietMetrix.onForeground()
        }
        val pageHide: (dynamic) -> Unit = { QuietMetrix.onBackground() }
        val pageShow: (dynamic) -> Unit = { event -> if (event.persisted == true) QuietMetrix.onForeground() }
        document.addEventListener("visibilitychange", visibility)
        window.addEventListener("pagehide", pageHide)
        window.addEventListener("pageshow", pageShow)
        removeBrowserLifecycle = {
            document.removeEventListener("visibilitychange", visibility)
            window.removeEventListener("pagehide", pageHide)
            window.removeEventListener("pageshow", pageShow)
        }
        if (document.visibilityState == "hidden") QuietMetrix.onBackground()
    }
    if (config.autoTrackInitialPageView && Gate.shouldTrack()) {
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch { trackEvent("page_view") }
    }
}

internal actual fun platformStop() {
    removeBrowserLifecycle?.invoke()
    removeBrowserLifecycle = null
}
