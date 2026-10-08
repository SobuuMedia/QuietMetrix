package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.createPersistentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The single process-wide [MetricRecorder] every counter producer records into —
 * `trackEvent`, [com.quietmetrix.analytics.internal.ScreenTracker], and on-device funnel/
 * session tracking — and the only thing [CounterFlusher] drains from. Callers pass metric
 * name and dims; this computes the device's calendar day so producers never have to.
 */
@OptIn(ExperimentalTime::class)
internal object MetricGateway {
    private var recorder = MetricRecorder()
    private var configuredPrefix: String? = null
    private val purgeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun configure(storageKeyPrefix: String) {
        if (configuredPrefix == storageKeyPrefix) return
        // Revocation can be followed immediately by a process kill, before the asynchronous
        // purge runs. A disabled launch must discard that old outbox before it can be restored.
        recorder = MetricRecorder(createPersistentStore(storageKeyPrefix), restorePending = com.quietmetrix.analytics.internal.Gate.shouldTrack())
        configuredPrefix = storageKeyPrefix
    }

    suspend fun record(metric: String, dims: Map<String, String>, n: Long = 1L, now: Instant = Clock.System.now()) {
        val day = utcTodayIso(now)
        if (metric == "unique_window_v2") {
            recorder.record(metric, dims, day = day, n = n)
            return
        }
        val windows = if (metric == "funnel_step_v2") {
            val entryDay = dims["entry_day"] ?: day
            listOf(mapOf("days" to "1", "end_day" to entryDay) to entryDay)
        } else {
            val origin = epochDayFromIso(day)
            val lengths = (1..7) + listOf(30, 90)
            lengths.flatMap { length ->
                (0 until length).map { offset ->
                    val endDay = civilDateFromEpochDay(origin + offset.toLong()).let { (year, month, date) ->
                        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${date.toString().padStart(2, '0')}"
                    }
                    mapOf("scope_kind" to "project", "scope_key" to "all", "days" to length.toString(), "end_day" to endDay) to endDay
                }
            }
        }
        val hour = if (metric in HOURLY_METRICS) ((now.toEpochMilliseconds() / 3_600_000L) % 24L).toInt() else null
        recorder.recordWithWindows(metric, dims, day, n, windows, hour)
        v2Alias(metric)?.let { alias ->
            recorder.recordWithWindows(alias, dims, day, n, windows, hour = hourOf(now))
        }
        if (metric in HOURLY_METRICS) {
            recorder.record("unique_hour_v2", mapOf("scope_kind" to "project", "scope_key" to "all"), day, hour = hourOf(now))
            dims["screen"]?.let { screen ->
                recorder.record("unique_hour_v2", mapOf("scope_kind" to "screen", "scope_key" to screen), day, hour = hourOf(now))
            }
        }
    }

    suspend fun drain(): List<MetricRecorder.PendingCounter> = recorder.drain()

    suspend fun prepareBatches(platform: String, appVersion: String?, countryCode: String?, sdkVersion: String, nowEpochMs: Long, deviceClass: String = "unknown") =
        recorder.prepareBatches(platform, appVersion, countryCode, sdkVersion, nowEpochMs, deviceClass)

    suspend fun acknowledge(batchId: String) = recorder.acknowledge(batchId)

    fun hasPersistenceFailure(): Boolean = recorder.hasPersistenceFailure()

    private fun hourOf(now: Instant): Int = ((now.toEpochMilliseconds() / 3_600_000L) % 24L).toInt()

    private fun v2Alias(metric: String): String? = when (metric) {
        "event" -> "event_v2"
        "screen_transition" -> "screen_transition_v2"
        "search" -> "search_v2"
        "search_zero_result" -> "search_zero_result_v2"
        else -> null
    }

    /** Discards any not-yet-flushed counters — called (fire-and-forget, mirroring the old
     *  `FlushManager.purgeQueue()`) when analytics is disabled mid-session, from a non-suspend
     *  call site ([com.quietmetrix.analytics.internal.Gate.setAnalyticsEnabled]). */
    fun purge() {
        val revokedRecorder = recorder
        purgeScope.launch { revokedRecorder.purge() }
    }

    /** Test-only: starts a fresh recorder so tests don't leak state into one another. */
    internal fun reset() {
        recorder = MetricRecorder()
        configuredPrefix = null
    }

    private val HOURLY_METRICS = setOf(
        "event_v2", "screen_transition_v2", "visit_start_v2", "visit_duration_ms_v2", "visit_complete_v2",
        "screen_view_v2", "screen_dwell_ms_v2", "screen_dwell_sample_v2", "search_v2", "search_zero_result_v2",
        "crash_v2", "error_v2",
    )
}
