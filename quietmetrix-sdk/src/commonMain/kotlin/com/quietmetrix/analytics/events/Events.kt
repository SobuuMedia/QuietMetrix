package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.EventValidator
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.experiments.ExperimentTracker

/** Records a valid aggregate event, then attributes it as a binary goal to eligible v2 tests. */
suspend fun trackEvent(event: String, screen: String? = null, props: Map<String, Any?> = emptyMap()) {
    if (!Gate.shouldTrack() || EventValidator.validate(event, screen, props).isNotEmpty()) return
    platformTrackEvent(event, screen, props)
    ExperimentTracker.onGoalEvent(event)
}

internal expect suspend fun platformTrackEvent(event: String, screen: String?, props: Map<String, Any?>)
