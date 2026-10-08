package com.quietmetrix.analytics.internal

import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.isTrackingAllowed
import com.quietmetrix.analytics.notifyExperimentDecisionObservers

internal object Gate {
    fun shouldTrack(): Boolean {
        val config = ConfigHolder.configOrNull ?: return false
        return ConfigHolder.isInitialized
            && isTrackingAllowed()
            && isAnalyticsEnabled()
    }

    fun isAnalyticsEnabled(): Boolean {
        val config = ConfigHolder.configOrNull ?: return true
        return runCatching { PersistentPreferences.get("analytics_enabled") in setOf(null, "1") }.getOrDefault(false)
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        val config = ConfigHolder.configOrNull ?: return
        PersistentPreferences.set("analytics_enabled", if (enabled) "1" else "0")
        if (!enabled) {
            MetricGateway.purge()
        } else {
            com.quietmetrix.analytics.QuietMetrix.onConsentGranted()
        }
        notifyExperimentDecisionObservers()
    }
}
