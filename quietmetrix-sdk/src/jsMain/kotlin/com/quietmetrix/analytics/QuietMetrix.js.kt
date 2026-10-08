package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.Gate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Browser events share the consent-gated aggregate recorder; no raw tracker is injected.
internal actual fun platformInit(config: QuietMetrixConfig) {
    if (config.autoTrackInitialPageView && Gate.shouldTrack()) {
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch { trackEvent("page_view") }
    }
}
