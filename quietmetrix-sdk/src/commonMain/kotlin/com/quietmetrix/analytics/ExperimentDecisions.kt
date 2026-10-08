package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.context.loadOrCreateBucketingId
import com.quietmetrix.analytics.internal.experiments.ExperimentClient
import com.quietmetrix.analytics.internal.experiments.ExperimentTracker
import com.quietmetrix.analytics.internal.experiments.experimentAudienceMatches
import com.quietmetrix.analytics.internal.experiments.experimentV2VariantForSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A side-effect-free answer for one app-authored experiment placement. */
sealed interface ExperimentDecision {
    data object Pending : ExperimentDecision
    data object NotEligible : ExperimentDecision
    data class Unavailable(val reason: UnavailableReason) : ExperimentDecision
    data class Ready(val key: String, val revision: Int, val variant: Variant) : ExperimentDecision
}

enum class Variant { A, B }
enum class UnavailableReason { NOT_INITIALIZED, CONFIGURATION_MISSING, ANALYTICS_DISABLED, INVALID_KEY }

/** A cancellable callback registration for SDKs and UI wrappers that need live config updates. */
class ExperimentSubscription internal constructor(private val cancelAction: () -> Unit) {
    private var closed = false
    fun close() {
        if (!closed) {
            closed = true
            cancelAction()
        }
    }
}

/** Simple Objective-C/Swift friendly projection of [ExperimentDecision]. */
data class ExperimentDecisionSnapshot(
    val key: String,
    val state: String,
    val variant: String? = null,
    val revision: Int = 0,
)

private val decisionObservers = mutableMapOf<String, MutableMap<Int, (ExperimentDecision) -> Unit>>()
private val lastResolvedDecisions = mutableMapOf<String, ExperimentDecision.Ready>()
private var nextObserverId = 0
private val exposureScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

private var currentCountryCode: String? = null
private var currentLanguageTag: String? = null

/**
 * Replaces the optional app-supplied audience dimensions used by experiment decisions.
 * Country and language are independent: a locale's region is never treated as country.
 * Pass null to clear either value. Existing visible placements keep their already-rendered UI.
 */
fun updateAudience(countryCode: String?, languageTag: String?) {
    currentCountryCode = countryCode?.trim()?.uppercase()?.takeIf(::isIsoCountryCode)
    currentLanguageTag = languageTag?.trim()?.substringBefore('-')?.substringBefore('_')
        ?.lowercase()?.takeIf { it.matches(Regex("^[a-z]{2,3}$")) }
    notifyExperimentDecisionObservers()
}

/** Shared only with the legacy name-based API; the device locale is never consulted. */
internal fun currentAudienceCountryCode(): String? = currentCountryCode

/** Observes a decision and immediately calls [listener] with the current state. Close to detach. */
fun observeExperiment(key: String, listener: (ExperimentDecision) -> Unit): ExperimentSubscription {
    val id = nextObserverId++
    decisionObservers.getOrPut(key) { mutableMapOf() }[id] = listener
    listener(resolveExperiment(key))
    return ExperimentSubscription {
        decisionObservers[key]?.remove(id)
        if (decisionObservers[key].isNullOrEmpty()) decisionObservers.remove(key)
    }
}

/** ObjC-friendly adapter for SwiftUI; subscriptions must be closed when their view leaves scope. */
fun observeExperimentState(key: String, listener: (ExperimentDecisionSnapshot) -> Unit): ExperimentSubscription =
    observeExperiment(key) { decision ->
        listener(
            when (decision) {
                ExperimentDecision.Pending -> ExperimentDecisionSnapshot(key, "pending")
                ExperimentDecision.NotEligible -> ExperimentDecisionSnapshot(key, "not_eligible")
                is ExperimentDecision.Unavailable -> ExperimentDecisionSnapshot(key, "unavailable")
                is ExperimentDecision.Ready -> ExperimentDecisionSnapshot(
                    decision.key, "ready", if (decision.variant == Variant.A) "a" else "b", decision.revision,
                )
            },
        )
    }

/** SwiftUI convenience that records only a currently Ready, active assignment. */
fun recordExperimentExposureForKey(key: String) {
    val ready = lastResolvedDecisions.values.lastOrNull { it.key == key } ?: return
    recordExperimentExposure(ready)
}

/** SwiftUI adapter that records the exact Ready snapshot already selected for its live view. */
fun recordExperimentExposureForSnapshot(key: String, revision: Int, variantId: String) {
    val ready = lastResolvedDecisions[decisionToken(key, revision, variantId)] ?: return
    val expectedVariant = when (variantId) {
        "a" -> Variant.A
        "b" -> Variant.B
        else -> return
    }
    if (ready.revision == revision && ready.variant == expectedVariant) recordExperimentExposure(ready)
}

internal fun notifyExperimentDecisionObservers() {
    decisionObservers.toMap().forEach { (key, listeners) ->
        val decision = resolveExperiment(key)
        listeners.values.toList().forEach { listener ->
            try { listener(decision) } catch (_: Exception) { /* A host observer cannot interrupt SDK state changes. */ }
        }
    }
}

/** Resolves a v2 experiment without recording an exposure or making a network request. */
fun resolveExperiment(key: String): ExperimentDecision {
    if (key.isBlank() || key.length > 64 || !key.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_.-]*$"))) {
        return ExperimentDecision.Unavailable(UnavailableReason.INVALID_KEY)
    }
    if (!QuietMetrix.isInitialized) return ExperimentDecision.Unavailable(UnavailableReason.NOT_INITIALIZED)
    if (!Gate.shouldTrack()) return ExperimentDecision.Unavailable(UnavailableReason.ANALYTICS_DISABLED)
    val spec = ExperimentClient.v2SpecFor(key) ?: return if (ExperimentClient.isV2ConfigLoaded()) {
        ExperimentDecision.Unavailable(UnavailableReason.CONFIGURATION_MISSING)
    } else ExperimentDecision.Pending

    if (!experimentAudienceMatches(spec.audience, currentCountryCode, currentLanguageTag)) return ExperimentDecision.NotEligible

    val selected = spec.selectedVariant
    val id = selected ?: run {
        val localSeed = loadOrCreateBucketingId() ?: return ExperimentDecision.Unavailable(UnavailableReason.ANALYTICS_DISABLED)
        experimentV2VariantForSeed(spec, localSeed)
    }
    val ready = when (id) {
        "a" -> ExperimentDecision.Ready(spec.key, spec.revision, Variant.A)
        "b" -> ExperimentDecision.Ready(spec.key, spec.revision, Variant.B)
        else -> ExperimentDecision.Unavailable(UnavailableReason.CONFIGURATION_MISSING)
    }
    if (ready is ExperimentDecision.Ready) {
        lastResolvedDecisions[decisionToken(ready.key, ready.revision, if (ready.variant == Variant.A) "a" else "b")] = ready
    }
    return ready
}

/**
 * Records a v2 exposure once for this installation and revision. Call only when the placement
 * actually reaches its render boundary; simply inspecting a decision does not count as exposure.
 * Forged or stale decisions, out-of-audience assignments, and shipped rollouts are ignored.
 */
fun recordExperimentExposure(decision: ExperimentDecision.Ready) {
    val variantId = if (decision.variant == Variant.A) "a" else "b"
    if (lastResolvedDecisions[decisionToken(decision.key, decision.revision, variantId)] != decision) return
    val spec = ExperimentClient.v2SpecFor(decision.key) ?: return
    if (spec.status != "active" || spec.revision != decision.revision) return
    exposureScope.launch {
        val stillActive = ExperimentClient.v2SpecFor(decision.key)
        if (stillActive?.status != "active" || stillActive.revision != decision.revision) return@launch
        ExperimentTracker.onV2Exposure(decision.key, decision.revision, variantId)
    }
}

private fun decisionToken(key: String, revision: Int, variantId: String) = "$key|$revision|$variantId"
