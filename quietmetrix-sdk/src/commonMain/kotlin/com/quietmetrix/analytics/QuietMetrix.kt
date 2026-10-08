package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.ScreenTracker
import com.quietmetrix.analytics.internal.counters.CounterFlusher
import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.internal.counters.RetentionReporter
import com.quietmetrix.analytics.internal.counters.SessionTracker
import com.quietmetrix.analytics.internal.context.clearLegacyAnonymousId
import com.quietmetrix.analytics.internal.experiments.ExperimentClient
import com.quietmetrix.analytics.internal.experiments.ExperimentTracker
import com.quietmetrix.analytics.internal.funnels.FunnelEvaluator
import com.quietmetrix.analytics.internal.funnels.FunnelRegistrar
import com.quietmetrix.analytics.internal.funnels.FunnelRemoteClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll

object QuietMetrix {
    private val initScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private data class LifecycleEvent(val config: QuietMetrixConfig?, val foreground: Boolean, val shutdown: Boolean = false, val barrier: CompletableDeferred<Unit>? = null)
    private val lifecycleEvents = Channel<LifecycleEvent>(Channel.UNLIMITED)
    private val lifecycleJob = initScope.launch {
            for (event in lifecycleEvents) {
                if (event.barrier != null) { event.barrier.complete(Unit); continue }
                if (ConfigHolder.configOrNull !== event.config) continue
                if (event.foreground) SessionTracker.start()
                else {
                    ScreenTracker.flush()
                    SessionTracker.stop()
                    if (event.shutdown) CounterFlusher.stop()
                    else event.config?.let { config -> initScope.launch { CounterFlusher.flush(config) } }
                }
            }
    }

    internal suspend fun awaitPendingWorkForTest() {
        val barrier = CompletableDeferred<Unit>()
        lifecycleEvents.send(LifecycleEvent(null, foreground = false, barrier = barrier))
        barrier.await()
        initScope.coroutineContext[Job]?.children?.filter { it != lifecycleJob }?.toList()?.joinAll()
    }

    fun init(config: QuietMetrixConfig) {
        if (config.trackingEndpoint != null) {
            require(config.trackingEndpoint.startsWith("https://")) {
                "trackingEndpoint must be https:// (received: ${config.trackingEndpoint}). " +
                "Null is allowed for offline mode."
            }
        }
        ConfigHolder.set(config)
        updateAudience(config.countryCode, config.languageTag)
        clearLegacyAnonymousId(config)
        MetricGateway.configure(config.storageKeyPrefix)
        SessionTracker.reset()
        onForeground()
        ExperimentTracker.reset()
        ExperimentClient.loadSnapshot(config)
        ExperimentClient.refreshInBackground(config)
        CounterFlusher.start(config)
        FunnelEvaluator.configure(config)
        platformInit(config)
        FunnelRemoteClient.refreshInBackground(config)
        FunnelRegistrar.registerIfChanged(config)
        // One-shot, fire-and-forget per launch — same tolerance as ScreenTracker.closeOutAsync.
        if (Gate.shouldTrack()) initScope.launch { RetentionReporter.reportIfDue(config) }
    }

    val isInitialized: Boolean get() = ConfigHolder.isInitialized

    /** Resumes consent-bound fetch, registration and reporting after consent changes in-session. */
    internal fun onConsentGranted() {
        if (!Gate.shouldTrack()) return
        val config = ConfigHolder.configOrNull ?: return
        onForeground()
        ExperimentClient.refreshInBackground(config)
        FunnelRemoteClient.refreshInBackground(config)
        FunnelRegistrar.registerIfChanged(config)
        initScope.launch { RetentionReporter.reportIfDue(config) }
        initScope.launch { CounterFlusher.flush(config) }
    }

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
        // Cancellation is immediate even when the lifecycle queue or host is shutting down.
        CounterFlusher.pause()
        ConfigHolder.configOrNull?.let { lifecycleEvents.trySend(LifecycleEvent(it, foreground = false, shutdown = true)) }
    }

    internal fun onForeground() {
        ConfigHolder.configOrNull?.let { lifecycleEvents.trySend(LifecycleEvent(it, foreground = true)) }
    }

    internal fun onBackground() {
        ConfigHolder.configOrNull?.let { lifecycleEvents.trySend(LifecycleEvent(it, foreground = false)) }
    }

}

internal expect fun platformInit(config: QuietMetrixConfig)
