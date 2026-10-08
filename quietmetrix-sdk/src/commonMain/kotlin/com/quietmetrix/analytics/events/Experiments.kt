package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.Gate
import com.quietmetrix.analytics.internal.context.loadOrCreateBucketingId
import com.quietmetrix.analytics.currentAudienceCountryCode
import com.quietmetrix.analytics.internal.experiments.ExperimentClient
import com.quietmetrix.analytics.internal.experiments.ExperimentTracker
import com.quietmetrix.analytics.internal.experiments.NONE_VARIANT
import com.quietmetrix.analytics.internal.experiments.VariantAssigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fire-and-forget scope for recording impression/interaction counters — same tolerance as
 *  every other counter producer in this SDK (a dropped counter is acceptable; a blocked
 *  caller is not). Plain top-level functions, not expect/actual: everything here is
 *  cross-platform logic with no platform-specific behavior, unlike `trackEvent`. */
private val experimentsScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

/**
 * The variant name to render for [experimentKey] — one of the two variant names declared for
 * that experiment in the dashboard, or `"none"` when this device isn't enrolled: the
 * experiment is unknown, not active, this device fell outside its traffic percentage, its
 * explicitly supplied country isn't in the experiment's allowlist, or analytics tracking isn't currently allowed.
 * Synchronous, never blocks on network, never throws. On the very first call for
 * [experimentKey] this session that resolves to a real variant, records one impression counter
 * (see [trackExperimentInteraction]).
 */
fun getVariant(experimentKey: String): String {
    if (!Gate.shouldTrack()) return NONE_VARIANT
    val spec = ExperimentClient.specFor(experimentKey) ?: return NONE_VARIANT
    val bucketingId = loadOrCreateBucketingId() ?: return NONE_VARIANT
    val variant = VariantAssigner.assign(spec, bucketingId, currentAudienceCountryCode())
    if (variant != NONE_VARIANT) {
        experimentsScope.launch { ExperimentTracker.onImpression(experimentKey, variant) }
    }
    return variant
}

/**
 * Records that the user interacted with whichever variant was shown for [experimentKey] this
 * session — e.g. tapped the button being tested. At most once per session. A no-op if
 * [getVariant] was never called for [experimentKey] this session, since there is then no
 * impression to attribute the interaction to.
 */
fun trackExperimentInteraction(experimentKey: String) {
    if (!Gate.shouldTrack()) return
    experimentsScope.launch { ExperimentTracker.onInteraction(experimentKey) }
}
