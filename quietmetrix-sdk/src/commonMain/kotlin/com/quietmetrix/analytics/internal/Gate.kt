package com.quietmetrix.analytics.internal

import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.isTrackingAllowed

internal object Gate {
    fun shouldTrack(): Boolean {
        val config = ConfigHolder.configOrNull ?: return false
        return ConfigHolder.isInitialized
            && isTrackingAllowed()
            && isAnalyticsEnabled()
    }

    fun isAnalyticsEnabled(): Boolean {
        val config = ConfigHolder.configOrNull ?: return true
        val key = StorageKeys.analyticsEnabled(config.storageKeyPrefix)
        val stored = InMemoryStore.get(key)
        return stored != "0"
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        val config = ConfigHolder.configOrNull ?: return
        val key = StorageKeys.analyticsEnabled(config.storageKeyPrefix)
        InMemoryStore.set(key, if (enabled) "1" else "0")
        if (!enabled) {
            MetricGateway.purge()
        }
    }
}