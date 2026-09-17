package com.quietmetrix.analytics.internal.funnels

import com.quietmetrix.analytics.Funnel
import com.quietmetrix.analytics.FunnelCountMode
import com.quietmetrix.analytics.FunnelManifest
import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.PersistentStore
import com.quietmetrix.analytics.internal.counters.MetricGateway
import com.quietmetrix.analytics.internal.createPersistentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * On-device replacement for server-side funnel matching (FunnelAnalyzer.kt /
 * funnelAnalyze.php): as the app's own events happen in real time, this tracks how far each
 * configured funnel has progressed and records only the depth reached — a
 * `funnel_step{f, rev, step}` counter — never the underlying event trail a server-side matcher
 * would need to store. `step` is the 1-based count of steps reached so far (not the step's
 * declared key), recorded once per new depth, so a server-side query can compute "N% of
 * devices reached step k" for each k.
 *
 * [FunnelCountMode.ACTOR] tracks one lifetime progress per funnel, persisted so a multi-day
 * `windowSeconds` genuinely spans app restarts (see [loadActorProgress]/[saveActorProgress]).
 * Once a window expires or the funnel completes, that funnel is permanently resolved — no
 * later, independent attempt creates a second entry, matching the historical server-side
 * semantics devs already rely on (`FunnelMatcher`'s "an actor's entry is fixed at their FIRST
 * step-0 match").
 *
 * [FunnelCountMode.ATTEMPT] instead tracks one progress per distinct value of
 * [Funnel.correlationProperty] seen on a matching event, so concurrent attempts (e.g. two
 * in-flight checkouts) don't merge. This is in-memory only for now — a known, documented
 * limitation (an app killed mid-attempt loses that attempt's progress) — unlike ACTOR mode.
 *
 * Real-time evaluation also means the server-side out-of-order/duplicate-timestamp guard
 * (`FunnelMatcher`'s `previousTs` check) isn't needed here: a single device processes its own
 * events in the order they actually happened.
 */
@OptIn(ExperimentalTime::class)
internal object FunnelEvaluator {

    @Serializable
    internal data class Progress(val entryMs: Long, val deadlineMs: Long, val reachedStepIndex: Int)

    private val json = Json { encodeDefaults = true }

    private var manifest: FunnelManifest? = null
    private var storageKeyPrefix: String = ""
    private var store: PersistentStore? = null

    private val actorProgressCache = mutableMapOf<String, Progress?>()
    private val attemptProgress = mutableMapOf<String, MutableMap<String, Progress>>()
    private const val MAX_ATTEMPTS_PER_FUNNEL = 1_000

    fun configure(config: QuietMetrixConfig) {
        manifest = config.funnelManifest
            ?: config.funnels.takeIf { it.isNotEmpty() }?.let { FunnelManifest("legacy", 0, it) }
        if (storageKeyPrefix != config.storageKeyPrefix) {
            actorProgressCache.clear()
            attemptProgress.clear()
        }
        storageKeyPrefix = config.storageKeyPrefix
        store = createPersistentStore(config.storageKeyPrefix)
    }

    suspend fun onEvent(event: String, screen: String?, props: Map<String, Any?>, now: Instant = Clock.System.now()) {
        val current = manifest ?: return
        for (funnel in current.funnels) {
            if (funnel.steps.isEmpty()) continue
            when (funnel.countMode) {
                FunnelCountMode.ACTOR -> evaluateActor(current, funnel, event, screen, props, now)
                FunnelCountMode.ATTEMPT -> evaluateAttempt(current, funnel, event, screen, props, now)
            }
        }
    }

    private suspend fun evaluateActor(manifest: FunnelManifest, funnel: Funnel, event: String, screen: String?, props: Map<String, Any?>, now: Instant) {
        // No storageKeyPrefix here: createPersistentStore(config.storageKeyPrefix) already
        // namespaces every key by that prefix internally (see FileBasedPersistentStore) --
        // repeating it here would just double it in the backing filename.
        val storageKey = "funnel_progress_${funnel.key}_r${manifest.revision}"
        val current = loadActorProgress(storageKey)
        val advanced = tryAdvance(funnel, current, event, screen, props, now) ?: return
        saveActorProgress(storageKey, advanced)
        emitStep(manifest, funnel, advanced.reachedStepIndex, now)
    }

    private suspend fun evaluateAttempt(manifest: FunnelManifest, funnel: Funnel, event: String, screen: String?, props: Map<String, Any?>, now: Instant) {
        val correlationKey = funnel.correlationProperty ?: return
        val correlationValue = props[correlationKey]?.toString() ?: return
        val byCorrelation = attemptProgress.getOrPut(funnel.key) { mutableMapOf() }

        val current = byCorrelation[correlationValue]
        val advanced = tryAdvance(funnel, current, event, screen, props, now) ?: return
        byCorrelation[correlationValue] = advanced
        pruneAttempts(byCorrelation, funnel.steps.size, now)
        emitStep(manifest, funnel, advanced.reachedStepIndex, now)
    }

    /** Null if [event] doesn't advance this funnel from [current] (wrong next step, already
     *  resolved, or window expired) — otherwise the new [Progress] to persist. */
    private fun tryAdvance(funnel: Funnel, current: Progress?, event: String, screen: String?, props: Map<String, Any?>, now: Instant): Progress? {
        val steps = funnel.steps
        if (current != null && current.reachedStepIndex >= steps.size) return null
        if (current != null && now.toEpochMilliseconds() > current.deadlineMs) return null

        val nextIndex = current?.reachedStepIndex ?: 0
        if (!funnelStepMatches(steps[nextIndex], event, screen, props)) return null

        val entryMs = current?.entryMs ?: now.toEpochMilliseconds()
        val deadlineMs = current?.deadlineMs ?: (now.toEpochMilliseconds() + funnel.windowSeconds * 1000)
        return Progress(entryMs, deadlineMs, nextIndex + 1)
    }

    private suspend fun emitStep(manifest: FunnelManifest, funnel: Funnel, reachedStepIndex: Int, now: Instant) {
        MetricGateway.record(
            "funnel_step",
            mapOf("f" to funnel.key, "rev" to manifest.revision.toString(), "step" to reachedStepIndex.toString()),
            now = now,
        )
    }

    private fun pruneAttempts(byCorrelation: MutableMap<String, Progress>, stepCount: Int, now: Instant) {
        if (byCorrelation.size < MAX_ATTEMPTS_PER_FUNNEL) return
        val nowMs = now.toEpochMilliseconds()
        byCorrelation.entries.removeAll { (_, p) -> p.reachedStepIndex >= stepCount || nowMs > p.deadlineMs }
    }

    private fun loadActorProgress(storageKey: String): Progress? {
        if (actorProgressCache.containsKey(storageKey)) return actorProgressCache[storageKey]
        val progress = store?.get(storageKey)?.let { raw -> runCatching { json.decodeFromString<Progress>(raw) }.getOrNull() }
        actorProgressCache[storageKey] = progress
        return progress
    }

    private fun saveActorProgress(storageKey: String, progress: Progress) {
        actorProgressCache[storageKey] = progress
        store?.set(storageKey, json.encodeToString(progress))
    }

    /** Test-only: clears in-memory state (not the persisted store — ACTOR-mode progress is
     *  meant to survive exactly this). */
    internal fun reset() {
        manifest = null
        storageKeyPrefix = ""
        store = null
        actorProgressCache.clear()
        attemptProgress.clear()
    }
}
