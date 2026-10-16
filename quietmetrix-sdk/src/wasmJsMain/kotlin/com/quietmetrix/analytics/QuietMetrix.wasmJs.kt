package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.Gate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Browser events share the consent-gated aggregate recorder; no raw tracker is injected.
private var removeBrowserLifecycle: (() -> Unit)? = null

@JsFun("(onHidden, onVisible) => { if (typeof document === 'undefined' || typeof window === 'undefined') return null; const visibility = () => document.visibilityState === 'hidden' ? onHidden() : onVisible(); const hide = () => onHidden(); const show = e => { if (e.persisted) onVisible(); }; document.addEventListener('visibilitychange', visibility); window.addEventListener('pagehide', hide); window.addEventListener('pageshow', show); if (document.visibilityState === 'hidden') onHidden(); return () => { document.removeEventListener('visibilitychange', visibility); window.removeEventListener('pagehide', hide); window.removeEventListener('pageshow', show); }; }")
private external fun installBrowserLifecycle(onHidden: () -> Unit, onVisible: () -> Unit): (() -> Unit)?

internal actual fun platformInit(config: QuietMetrixConfig) {
    removeBrowserLifecycle?.invoke()
    removeBrowserLifecycle = installBrowserLifecycle({ QuietMetrix.onBackground() }, { QuietMetrix.onForeground() })
    if (config.autoTrackInitialPageView && Gate.shouldTrack()) {
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch { trackEvent("page_view") }
    }
}

internal actual fun platformStop() {
    removeBrowserLifecycle?.invoke()
    removeBrowserLifecycle = null
}
