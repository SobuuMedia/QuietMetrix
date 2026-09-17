package com.quietmetrix.server.counters

import java.security.MessageDigest

/**
 * Structural validation and canonicalization for counter cells. A counter is
 * `(metric, dims) -> n`; this registry is what stands between arbitrary SDK input and the
 * `counters` table, so it is the enforcement point for "no free-form strings except in
 * threshold-gated fields": an unknown metric, an undeclared dimension, or a value outside the
 * allowed charset is rejected here rather than persisted.
 *
 * Mirrors php-hosting/src/counterRegistry.php exactly, including the canonicalization
 * algorithm — the two must hash identical dims to the identical value, or the same logical
 * cell silently splits into two rows across backends. Keep both in sync; see
 * CounterRegistryTest.kt for the shared vectors.
 */
object CounterRegistry {

    /** One declared metric: its exact required set of dimension keys. */
    data class MetricSpec(val name: String, val requiredDims: Set<String>)

    /**
     * v1 metrics. Each requires exactly this set of dims — no more, no fewer — so canonical
     * form never needs to encode which keys were present, only their values in sorted-key
     * order.
     */
    val metrics: Map<String, MetricSpec> = listOf(
        MetricSpec("screen_transition", setOf("from", "to")),
        MetricSpec("screen_dwell", setOf("screen", "bucket")),
        MetricSpec("funnel_step", setOf("f", "rev", "step")),
        MetricSpec("retention", setOf("cohort", "day")),
        MetricSpec("session", setOf("bucket")),
        MetricSpec("event", setOf("name")),
        MetricSpec("value", setOf("name")),
        MetricSpec("activation", setOf("cohort")),
        MetricSpec("search", setOf("screen")),
        MetricSpec("search_zero_result", setOf("screen")),
        MetricSpec("friction", setOf("screen", "kind")),
    ).associateBy { it.name }

    private val KEY_REGEX = Regex("^[a-z0-9_]{1,32}$")
    private val VALUE_REGEX = Regex("^[A-Za-z0-9_.:/-]{1,128}$")

    /** ASCII unit/record separators. Values are charset-restricted above so neither can ever
     *  appear inside a key or value, making this join unambiguous without percent-encoding. */
    private const val UNIT_SEP = '\u001F'
    private const val RECORD_SEP = '\u001E'

    sealed class ValidationResult {
        data class Valid(val canonicalDims: String, val dimsHash: String) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /** Validates [dims] against the declared shape of [metric] and, if valid, canonicalizes
     *  and hashes them. The hash is the grouping key callers use to upsert a counter cell. */
    fun validate(metric: String, dims: Map<String, String>): ValidationResult {
        val spec = metrics[metric] ?: return ValidationResult.Invalid("unknown_metric")
        if (dims.keys != spec.requiredDims) return ValidationResult.Invalid("dims_mismatch")
        for ((key, value) in dims) {
            if (!KEY_REGEX.matches(key)) return ValidationResult.Invalid("dim_key_invalid")
            if (!VALUE_REGEX.matches(value)) return ValidationResult.Invalid("dim_value_invalid")
        }
        val canonical = canonicalize(dims)
        return ValidationResult.Valid(canonical, sha256Hex(canonical))
    }

    private fun canonicalize(dims: Map<String, String>): String =
        dims.entries
            .sortedBy { it.key }
            .joinToString(RECORD_SEP.toString()) { (key, value) -> "$key$UNIT_SEP$value" }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
