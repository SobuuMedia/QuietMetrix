package com.quietmetrix.analytics.internal.transport

import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.StorageKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
internal object FlushManager {
    private var flushJob: Job? = null
    private var scope: CoroutineScope? = null
    private var connectivityMonitor: ConnectivityMonitor? = null
    private var isOnline: Boolean = true
    private var consecutiveFailures: Int = 0
    private val maxBackoffMs: Long = 60_000L
    private val sentEventFingerprints = mutableSetOf<String>()
    private const val MAX_FINGERPRINT_CACHE = 10_000

    fun start(config: QuietMetrixConfig) {
        val cs = scope ?: CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = cs
        flushJob?.cancel()
        flushJob = cs.launch {
            while (isActive) {
                val interval = currentBackoffMs()
                if (interval > 0L) {
                    delay(interval)
                } else {
                    delay(config.flushIntervalMs)
                }
                flush(config)
            }
        }
        restoreQueue(config)
        startConnectivityMonitor(config)
    }

    fun stop() {
        persistQueue()
        flushJob?.cancel()
        flushJob = null
        connectivityMonitor?.close()
        connectivityMonitor = null
        scope = null
    }

    suspend fun flush(config: QuietMetrixConfig) {
        if (!isOnline) return
        val endpoint = config.trackingEndpoint ?: return
        val apiKey = config.apiKey ?: return
        if (EventQueue.isEmpty()) return

        val batch = EventQueue.drain(100)
        if (batch.isEmpty()) return

        val deduped = batch.filter { event ->
            val fingerprint = "${event.event}:${event.ts}:${event.screen}"
            if (fingerprint in sentEventFingerprints) return@filter false
            sentEventFingerprints.add(fingerprint)
            if (sentEventFingerprints.size > MAX_FINGERPRINT_CACHE) {
                sentEventFingerprints.clear()
            }
            true
        }
        if (deduped.isEmpty()) return

        val enrichedBatch = deduped.map { enrichWithUserId(it, config) }

        try {
            val result = HttpTransport.send(endpoint, apiKey, enrichedBatch)
            if (result.success) {
                consecutiveFailures = 0
                log("Flush succeeded: ${enrichedBatch.size} events")
            } else if (result.retryable) {
                EventQueue.reEnqueue(batch)
                consecutiveFailures++
                log("Flush failed (retryable), re-enqueued ${batch.size} events (failure #$consecutiveFailures)")
            } else {
                consecutiveFailures = 0
                log("Flush failed (non-retryable): ${enrichedBatch.size} events dropped")
            }
        } catch (_: Exception) {
            EventQueue.reEnqueue(batch)
            consecutiveFailures++
            log("Flush error (retryable), re-enqueued ${batch.size} events (failure #$consecutiveFailures)")
        }
    }

    fun purgeQueue() {
        scope?.launch { EventQueue.clear() }
    }

    private fun startConnectivityMonitor(config: QuietMetrixConfig) {
        try {
            val monitor = ConnectivityMonitor()
            connectivityMonitor = monitor
            isOnline = monitor.isOnline
            monitor.onConnectivityChange { online ->
                val wasOffline = !isOnline
                isOnline = online
                if (wasOffline && online) {
                    consecutiveFailures = 0
                    val cs = scope ?: return@onConnectivityChange
                    cs.launch { flush(config) }
                }
            }
        } catch (_: Exception) {
            isOnline = true
        }
    }

    private fun enrichWithUserId(event: EnqueuedEvent, config: QuietMetrixConfig): EnqueuedEvent {
        val key = StorageKeys.identifiedUser(config.storageKeyPrefix)
        val userId = InMemoryStore.get(key)
        return if (userId != null && event.userId == null) {
            event.copy(userId = userId)
        } else {
            event
        }
    }

    internal fun currentBackoffMs(): Long {
        if (consecutiveFailures == 0) return 0L
        val baseDelay = 1000L
        val delay = baseDelay * (1L shl (consecutiveFailures - 1))
        return delay.coerceAtMost(maxBackoffMs)
    }

    private fun persistQueue() {
        ConfigHolder.configOrNull?.let { config ->
            scope?.launch {
                val events = EventQueue.persistToStore()
                if (events.isNotEmpty()) {
                    val dtos = events.map { it.toRequest() }
                    val jsonStr = Json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(TrackEventRequestSerializer),
                        dtos
                    )
                    InMemoryStore.set("${config.storageKeyPrefix}queue_persist", jsonStr)
                }
            }
        }
    }

    private fun restoreQueue(config: QuietMetrixConfig) {
        val jsonStr = InMemoryStore.get("${config.storageKeyPrefix}queue_persist") ?: return
        try {
            val dtos: List<TrackEventRequestDto> = Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(TrackEventRequestSerializer),
                jsonStr
            )
            scope?.launch {
                dtos.forEach { dto ->
                    EventQueue.enqueue(
                        EnqueuedEvent(
                            event = dto.event,
                            screen = dto.screen,
                            props = dto.props?.let { it.mapValues { entry -> entry.value.toString() } } ?: emptyMap(),
                            sid = dto.sid,
                            ts = Instant.parse(dto.ts),
                            wasOffline = dto.was_offline,
                            userId = dto.uid,
                            sdk = dto.sdk?.let { SdkInfo(it.platform, it.version) },
                            ctx = dto.ctx?.let { EventContext(it.referrer, it.language, it.ua, it.viewport) },
                        )
                    )
                }
            }
        } catch (_: Exception) {
            log("Failed to restore persisted queue")
        }
    }

    private fun log(msg: String) {
        if (ConfigHolder.configOrNull?.debug == true) {
            println("[QuietMetrix] $msg")
        }
    }
}
