package com.quietmetrix.server.counters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Structural validation + canonicalization for counter cells. Mirrors
 * php-hosting/src/counterRegistry.php — the two must canonicalize and hash identically or the
 * same logical cell splits into two rows across backends. Keep the vectors in this file and
 * that one bit-for-bit identical.
 */
class CounterRegistryTest {

    @Test
    fun `unknown metric is invalid`() {
        val result = CounterRegistry.validate("not_a_real_metric", mapOf("from" to "Library"))
        assertIs<CounterRegistry.ValidationResult.Invalid>(result)
        assertEquals("unknown_metric", result.reason)
    }

    @Test
    fun `missing a required dim is invalid`() {
        val result = CounterRegistry.validate("screen_transition", mapOf("from" to "Library"))
        assertIs<CounterRegistry.ValidationResult.Invalid>(result)
        assertEquals("dims_mismatch", result.reason)
    }

    @Test
    fun `an extra undeclared dim is invalid`() {
        val result = CounterRegistry.validate(
            "screen_transition",
            mapOf("from" to "Library", "to" to "BookDetail", "extra" to "x"),
        )
        assertIs<CounterRegistry.ValidationResult.Invalid>(result)
        assertEquals("dims_mismatch", result.reason)
    }

    @Test
    fun `a value outside the allowed charset is invalid`() {
        val result = CounterRegistry.validate(
            "screen_transition",
            mapOf("from" to "Library", "to" to "Book Detail!"),
        )
        assertIs<CounterRegistry.ValidationResult.Invalid>(result)
        assertEquals("dim_value_invalid", result.reason)
    }

    @Test
    fun `a key outside the allowed charset is invalid`() {
        val result = CounterRegistry.validate(
            "screen_transition",
            mapOf("FROM" to "Library", "to" to "BookDetail"),
        )
        assertIs<CounterRegistry.ValidationResult.Invalid>(result)
    }

    @Test
    fun `a valid cell canonicalizes independent of input key order`() {
        val a = CounterRegistry.validate("screen_transition", mapOf("from" to "Library", "to" to "BookDetail"))
        val b = CounterRegistry.validate("screen_transition", mapOf("to" to "BookDetail", "from" to "Library"))
        assertIs<CounterRegistry.ValidationResult.Valid>(a)
        assertIs<CounterRegistry.ValidationResult.Valid>(b)
        assertEquals(a.canonicalDims, b.canonicalDims)
        assertEquals(a.dimsHash, b.dimsHash)
    }

    @Test
    fun `canonicalization and hash match a fixed shared vector`() {
        // Shared vector — servers/ktor CounterRegistryTest and php-hosting counterRegistryTest
        // must produce this exact hash for this exact input, or the two backends split one
        // logical cell into two rows.
        val result = CounterRegistry.validate("screen_transition", mapOf("from" to "Library", "to" to "BookDetail"))
        assertIs<CounterRegistry.ValidationResult.Valid>(result)
        assertEquals("4b3584b7f1d30836d77378aa3e351dce36ea7ae9f8c101ae0633dd31159e4594", result.dimsHash)
    }

    @Test
    fun `swapping which value goes with which key changes the hash`() {
        // Guards against a naive no-separator concatenation, where {from:"AB",to:"C"} and
        // {from:"A",to:"BC"} would canonicalize to the same string.
        val a = CounterRegistry.validate("screen_transition", mapOf("from" to "Library", "to" to "BookDetail"))
        val b = CounterRegistry.validate("screen_transition", mapOf("from" to "BookDetail", "to" to "Library"))
        assertIs<CounterRegistry.ValidationResult.Valid>(a)
        assertIs<CounterRegistry.ValidationResult.Valid>(b)
        assertFalse(a.dimsHash == b.dimsHash)
    }

    @Test
    fun `every declared v1 metric validates a well-formed example`() {
        val examples = mapOf(
            "screen_transition" to mapOf("from" to "Library", "to" to "BookDetail"),
            "screen_dwell" to mapOf("screen" to "BookDetail", "bucket" to "5_10s"),
            "funnel_step" to mapOf("f" to "checkout", "rev" to "7", "step" to "3"),
            "retention" to mapOf("cohort" to "2026-W31", "day" to "7"),
            "session" to mapOf("bucket" to "1_3"),
            "event" to mapOf("name" to "page_view"),
            "value" to mapOf("name" to "purchase"),
            "activation" to mapOf("cohort" to "2026-W31"),
            "search" to mapOf("screen" to "Library"),
            "search_zero_result" to mapOf("screen" to "Library"),
            "friction" to mapOf("screen" to "Checkout", "kind" to "rage_tap"),
            "experiment" to mapOf("exp" to "checkout_cta", "variant" to "b", "action" to "impression"),
            "unique_window_v2" to mapOf("scope_kind" to "project", "scope_key" to "all", "days" to "7", "end_day" to "2026-10-04"),
        )
        for ((metric, dims) in examples) {
            val result = CounterRegistry.validate(metric, dims)
            assertTrue(result is CounterRegistry.ValidationResult.Valid, "$metric should validate: $result")
        }
    }

    /**
     * Shared vector with php-hosting/tests/counterRegistryTest.php — both backends must
     * produce this exact canonical string and hash for the A/B testing "experiment" metric,
     * or the same logical impression/interaction cell splits into two rows across backends.
     */
    @Test
    fun `experiment metric canonicalizes and hashes to the shared vector`() {
        val result = CounterRegistry.validate(
            "experiment",
            mapOf("exp" to "checkout_cta", "variant" to "b", "action" to "impression"),
        )
        assertIs<CounterRegistry.ValidationResult.Valid>(result)
        assertEquals("f7dec88b3cd61e3b865e98c0b56991eb1f61e241ccdf381fe15f0bd9b9175c2f", result.dimsHash)
    }

    @Test
    fun `experiment metric missing action is invalid`() {
        val result = CounterRegistry.validate("experiment", mapOf("exp" to "checkout_cta", "variant" to "b"))
        assertIs<CounterRegistry.ValidationResult.Invalid>(result)
        assertEquals("dims_mismatch", result.reason)
    }

    @Test
    fun `experiment metric with an extra key is invalid`() {
        val result = CounterRegistry.validate(
            "experiment",
            mapOf("exp" to "checkout_cta", "variant" to "b", "action" to "impression", "extra" to "x"),
        )
        assertIs<CounterRegistry.ValidationResult.Invalid>(result)
        assertEquals("dims_mismatch", result.reason)
    }
}
