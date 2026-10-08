package com.quietmetrix.analytics.internal.experiments

/** The variant name returned when a device is not enrolled in an experiment (country-excluded,
 *  outside the traffic percentage, or the experiment isn't active/known). */
internal const val NONE_VARIANT = "none"

/**
 * Deterministic, on-device variant assignment: a device with a stable [bucketingId] always
 * resolves the same experiment to the same variant, with no server round trip and no
 * per-device record ever leaving the device. Two independent hashes (`enroll`, `split`) keep
 * enrollment monotonic in [ExperimentSpec.trafficPercent] — raising it only adds devices, never
 * moves an already-enrolled device to the other variant. See Fnv1aTest / VariantAssignerTest
 * for the pinned vectors this must reproduce exactly, on every platform.
 */
internal object VariantAssigner {

    fun assign(spec: ExperimentSpec, bucketingId: String, countryCode: String?): String {
        val countries = spec.countries
        if (countries != null && (countryCode == null || countryCode !in countries)) {
            return NONE_VARIANT
        }
        if (bucketOf("${spec.key}:enroll:$bucketingId") >= spec.trafficPercent) {
            return NONE_VARIANT
        }
        val (baseline, challenger) = spec.variants
        return if (bucketOf("${spec.key}:split:$bucketingId") < baseline.weight) baseline.name else challenger.name
    }
}
