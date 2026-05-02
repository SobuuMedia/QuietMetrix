package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.StorageKeys
import com.quietmetrix.analytics.internal.transport.EventQueue
import com.quietmetrix.analytics.internal.transport.FlushManager

object QuietMetrix {
    fun init(config: QuietMetrixConfig) {
        if (config.trackingEndpoint != null) {
            require(config.trackingEndpoint.startsWith("https://")) {
                "trackingEndpoint must be https:// (received: ${config.trackingEndpoint}). " +
                "Null is allowed for offline mode."
            }
        }
        ConfigHolder.set(config)
        EventQueue.configure(config.maxQueueSize)
        platformInit(config)
        FlushManager.start(config)
    }

    val isInitialized: Boolean get() = ConfigHolder.isInitialized

    suspend fun flush() {
        ConfigHolder.configOrNull?.let { config ->
            config.trackingEndpoint?.let { _ ->
                FlushManager.flush(config)
            }
        }
    }

    fun identify(userId: String?) {
        ConfigHolder.configOrNull?.let { config ->
            val key = StorageKeys.identifiedUser(config.storageKeyPrefix)
            if (userId != null) {
                InMemoryStore.set(key, userId)
            } else {
                InMemoryStore.remove(key)
            }
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        Gate.setAnalyticsEnabled(enabled)
    }

    val isAnalyticsEnabled: Boolean get() = Gate.isAnalyticsEnabled()

    fun stop() {
        FlushManager.stop()
    }
}

internal expect fun platformInit(config: QuietMetrixConfig)
