package com.quietmetrix.analytics.internal.counters

import com.quietmetrix.analytics.internal.PersistentStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Stores only anonymous aggregate deltas. No install or user identifier is present in this state. */
internal class MetricRecorder(
    private val store: PersistentStore? = null,
    private val storageKey: String = "counter_pipeline_v1",
    restorePending: Boolean = true,
) {
    @Serializable
    data class PendingCounter(
        val metric: String,
        val dims: Map<String, String>,
        val day: String,
        val n: Long,
        val isNewDevice: Boolean,
        val hour: Int? = null,
    )

    @Serializable
    data class QueuedBatch(
        val id: String,
        val day: String,
        val counters: List<PendingCounter>,
        val platform: String,
        val appVersion: String?,
        val countryCode: String?,
        val sdkVersion: String,
        val createdAtEpochMs: Long,
        val hour: Int? = null,
        val deviceClass: String = "unknown",
    )

    @Serializable
    private data class StoredCell(val metric: String, val canonicalDims: String, val day: String, val hour: Int? = null)

    @Serializable
    private data class StoredWindow(val key: String, val endDay: String)

    @Serializable
    private data class StoredState(
        val pending: List<PendingCounter> = emptyList(),
        val seenCells: List<StoredCell> = emptyList(),
        val seenWindows: List<StoredWindow> = emptyList(),
        val newestDaySeen: String? = null,
        val outbox: List<QueuedBatch> = emptyList(),
    )

    private data class Cell(val metric: String, val canonicalDims: String, val day: String, val hour: Int?)
    private class Accumulated(var n: Long, val isNewDevice: Boolean, val dims: Map<String, String>)

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val pending = LinkedHashMap<Cell, Accumulated>()
    private val seenCells = HashSet<Cell>()
    private val seenWindows = LinkedHashMap<String, String>()
    private val outbox = LinkedHashMap<String, QueuedBatch>()
    private var newestDaySeen: String? = null
    private var persistenceFailure: Boolean = false

    init {
        if (restorePending) restore()
        else runCatching { store?.remove(storageKey) }.onFailure { persistenceFailure = true }
    }

    suspend fun record(metric: String, dims: Map<String, String>, day: String, n: Long = 1L, hour: Int? = null) {
        mutex.withLock {
            if (persistenceFailure) return
            val cell = Cell(metric, canonicalize(dims), day, hour)
            val isNewDevice = seenCells.add(cell)
            if (metric == "unique_hour_v2" && !isNewDevice) return
            val existing = pending[cell]
            if (existing == null) pending[cell] = Accumulated(n, isNewDevice, dims.toMap()) else existing.n += n
            pruneSeenCells(day)
            persist()
        }
    }

    /** Records an ordinary cell and its anonymous once-per-window contributions in one snapshot. */
    suspend fun recordWithWindows(
        metric: String,
        dims: Map<String, String>,
        day: String,
        n: Long,
        windows: List<Pair<Map<String, String>, String>>,
        hour: Int? = null,
    ): Unit = mutex.withLock {
        if (persistenceFailure) return
        recordCell(metric, dims, day, n, hour)
        // V2 activity metrics have parallel daily and hourly projections. Today queries read
        // hourly cells; historical reports read daily cells and never sum both resolutions.
        // Legacy names deliberately keep their original single daily-compatible projection.
        if (metric.endsWith("_v2") && hour != null) recordCell(metric, dims, day, n, null)
        windows.forEach { (windowDims, endDay) ->
            val windowKey = canonicalize(windowDims)
            if (metric != "funnel_step_v2" && !seenWindows.containsKey(windowKey)) {
                seenWindows[windowKey] = endDay
                recordCell("unique_window_v2", windowDims, day, 1L, null)
            }
            if (metric.endsWith("_v2") && metric !in NON_WITNESS_METRICS) {
                val targetHash = sha256Hex(canonicalize(dims))
                val witnessDims = mapOf(
                    "target_metric" to metric,
                    "target_hash" to targetHash,
                    "days" to windowDims.getValue("days"),
                    "end_day" to endDay,
                )
                val witnessKey = "witness:${canonicalize(witnessDims)}"
                if (!seenWindows.containsKey(witnessKey)) {
                    val expiry = if (metric == "funnel_step_v2") {
                        val entry = epochDayFromIso(endDay) + 89L
                        civilDateFromEpochDay(entry).let { (year, month, date) ->
                            "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${date.toString().padStart(2, '0')}"
                        }
                    } else endDay
                    seenWindows[witnessKey] = expiry
                    recordCell("witness_window_v2", witnessDims, day, 1L, null)
                }
            }
        }
        seenWindows.entries.removeAll { it.value < day }
        pruneSeenCells(day)
        persist()
    }

    /** Atomically moves current deltas to a persisted retry outbox before any network send. */
    suspend fun prepareBatches(
        platform: String,
        appVersion: String?,
        countryCode: String?,
        sdkVersion: String,
        nowEpochMs: Long,
        deviceClass: String = "unknown",
    ): List<QueuedBatch> = mutex.withLock {
        if (persistenceFailure) return emptyList()
        expireOldBatches(nowEpochMs)
        if (pending.isNotEmpty() && outbox.size < MAX_OUTBOX_BATCHES) {
            val room = MAX_OUTBOX_BATCHES - outbox.size
            var created = 0
            // Kotlin/JS map entries are live views: snapshot before removing any key.
            for ((dayHour, entries) in pending.map { it.key to it.value }.groupBy { it.first.day to it.first.hour }) {
                if (created >= room) break
                val day = dayHour.first
                val hour = dayHour.second
                for (chunk in entries.chunked(MAX_COUNTERS_PER_BATCH)) {
                    if (created >= room) break
                    val counters = chunk.map { (cell, acc) -> PendingCounter(cell.metric, acc.dims, day, acc.n, acc.isNewDevice, hour) }
                    val id = newBatchId()
                    outbox[id] = QueuedBatch(id, day, counters, platform, appVersion, countryCode, sdkVersion, nowEpochMs, hour, deviceClass)
                    chunk.forEach { (cell, _) -> pending.remove(cell) }
                    created++
                }
            }
        }
        persist()
        if (persistenceFailure) emptyList() else outbox.values.toList()
    }

    suspend fun acknowledge(batchId: String) = mutex.withLock {
        if (outbox.remove(batchId) != null) persist()
    }

    /** Test/inspection helper: drains only live deltas; production uses [prepareBatches]. */
    suspend fun drain(): List<PendingCounter> = mutex.withLock {
        val result = pending.map { (cell, acc) -> PendingCounter(cell.metric, acc.dims, cell.day, acc.n, acc.isNewDevice, cell.hour) }
        pending.clear()
        persist()
        result
    }

    /** Consent revocation removes unsent aggregate data and local dedupe state. */
    suspend fun purge() = mutex.withLock {
        pending.clear()
        outbox.clear()
        seenCells.clear()
        seenWindows.clear()
        newestDaySeen = null
        store?.remove(storageKey)
        persistenceFailure = false
    }

    fun hasPersistenceFailure(): Boolean = persistenceFailure

    private fun restore() {
        val encoded = runCatching { store?.get(storageKey) }
            .onFailure { persistenceFailure = true }.getOrNull() ?: return
        runCatching { json.decodeFromString<StoredState>(encoded) }
            .onSuccess { state ->
                state.pending.forEach { item ->
            val cell = Cell(item.metric, canonicalize(item.dims), item.day, item.hour)
                    pending[cell] = Accumulated(item.n, item.isNewDevice, item.dims)
                }
                state.seenCells.forEach { seenCells += Cell(it.metric, it.canonicalDims, it.day, it.hour) }
                state.seenWindows.forEach { (key, endDay) -> seenWindows[key] = endDay }
                state.outbox.forEach { outbox[it.id] = it }
                newestDaySeen = state.newestDaySeen
            }
            .onFailure { persistenceFailure = true }
    }

    private fun persist() {
        val target = store ?: return
        val state = StoredState(
            pending = pending.map { (cell, acc) -> PendingCounter(cell.metric, acc.dims, cell.day, acc.n, acc.isNewDevice, cell.hour) },
            seenCells = seenCells.map { StoredCell(it.metric, it.canonicalDims, it.day, it.hour) },
            seenWindows = seenWindows.map { StoredWindow(it.key, it.value) },
            newestDaySeen = newestDaySeen,
            outbox = outbox.values.toList(),
        )
        val encoded = json.encodeToString(state)
        persistenceFailure = encoded.encodeToByteArray().size > MAX_STORED_BYTES ||
            runCatching { target.set(storageKey, encoded) }.isFailure
    }

    private fun expireOldBatches(nowEpochMs: Long) {
        val cutoff = nowEpochMs - MAX_BATCH_AGE_MS
        outbox.entries.removeAll { it.value.createdAtEpochMs < cutoff }
    }

    private fun newBatchId(): String = Random.nextBytes(16).joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private fun canonicalize(dims: Map<String, String>): String =
        dims.entries.sortedBy { it.key }.joinToString(RECORD_SEP.toString()) { (key, value) -> "$key$UNIT_SEP$value" }

    private fun pruneSeenCells(day: String) {
        if (day > (newestDaySeen ?: "")) newestDaySeen = day
        if (seenCells.size < MAX_SEEN_CELLS) return
        val cutoff = newestDaySeen ?: return
        seenCells.retainAll { it.day >= cutoff }
    }

    private fun recordCell(metric: String, dims: Map<String, String>, day: String, n: Long, hour: Int?) {
        val cell = Cell(metric, canonicalize(dims), day, hour)
        val isNewDevice = seenCells.add(cell)
        if (metric == "unique_hour_v2" && !isNewDevice) return
        val existing = pending[cell]
        if (existing == null) pending[cell] = Accumulated(n, isNewDevice, dims.toMap()) else existing.n += n
    }

    private companion object {
        const val UNIT_SEP = '\u001F'
        const val RECORD_SEP = '\u001E'
        const val MAX_OUTBOX_BATCHES = 128
        const val MAX_COUNTERS_PER_BATCH = 512
        const val MAX_BATCH_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_STORED_BYTES = 8 * 1024 * 1024
        const val MAX_SEEN_CELLS = 5_000
        val NON_WITNESS_METRICS = setOf(
            "unique_window_v2", "unique_hour_v2", "witness_window_v2", "retention_entry_v2",
            "retention_return_v2", "experiment_exposure_v2", "experiment_goal_v2",
            "sdk_presence_v2",
        )
    }
}
