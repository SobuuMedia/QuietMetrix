package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.counters.CounterFlusher
import com.quietmetrix.analytics.internal.counters.RetentionReporter
import com.quietmetrix.analytics.internal.counters.SessionTracker
import com.quietmetrix.analytics.internal.funnels.FunnelEvaluator
import com.quietmetrix.analytics.internal.funnels.FunnelRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object QuietMetrix {
    private val initScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun init(config: QuietMetrixConfig) {
        if (config.trackingEndpoint != null) {
            require(config.trackingEndpoint.startsWith("https://")) {
                "trackingEndpoint must be https:// (received: ${config.trackingEndpoint}). " +
                "Null is allowed for offline mode."
            }
        }
        ConfigHolder.set(config)
        platformInit(config)
        SessionTracker.start()
        CounterFlusher.start(config)
        FunnelEvaluator.configure(config)
        FunnelRegistrar.registerIfChanged(config)
        // One-shot, fire-and-forget per launch — same tolerance as ScreenTracker.closeOutAsync.
        initScope.launch { RetentionReporter.reportIfDue(config) }
    }

    val isInitialized: Boolean get() = ConfigHolder.isInitialized

    /** Forces an immediate flush of the counter pipeline — the aggregate-only path every
     *  `trackEvent`/`trackScreen` call records into. */
    suspend fun flush() {
        ConfigHolder.configOrNull?.let { config ->
            config.trackingEndpoint?.let { _ ->
                CounterFlusher.flush(config)
            }
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        Gate.setAnalyticsEnabled(enabled)
    }

    val isAnalyticsEnabled: Boolean get() = Gate.isAnalyticsEnabled()

    fun stop() {
        ScreenTracker.closeOutAsync()
        // Fire-and-forget, same tolerance as ScreenTracker.closeOutAsync — launched before
        // CounterFlusher.stop()'s own final flush so the session counter has a chance to land
        // in MetricGateway first, though neither is guaranteed to complete before process exit.
        initScope.launch { SessionTracker.stop() }
        CounterFlusher.stop()
    }
}

internal expect fun platformInit(config: QuietMetrixConfig)
