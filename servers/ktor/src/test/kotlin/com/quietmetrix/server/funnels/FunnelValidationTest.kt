package com.quietmetrix.server.funnels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FunnelValidationTest {

    private fun step(key: String, event: String = "some_event") =
        FunnelStepDefinition(key = key, event = event)

    private fun definition(
        funnelKey: String = "signup",
        name: String = "Signup",
        steps: List<FunnelStepDefinition> = listOf(step("view"), step("submit")),
        windowSeconds: Long = 604_800,
    ) = FunnelDefinition(funnelKey = funnelKey, name = name, steps = steps, windowSeconds = windowSeconds)

    @Test
    fun `a well-formed two-step funnel is valid`() {
        val result = FunnelValidation.validate(definition(), existingFunnelCount = 0)
        assertEquals(FunnelValidationResult.Valid, result)
    }

    @Test
    fun `rejects a funnel key with uppercase or spaces`() {
        val result = FunnelValidation.validate(definition(funnelKey = "Sign Up"), existingFunnelCount = 0)
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `accepts a funnel key with hyphens and underscores`() {
        val result = FunnelValidation.validate(definition(funnelKey = "signup-v2_beta"), existingFunnelCount = 0)
        assertEquals(FunnelValidationResult.Valid, result)
    }

    @Test
    fun `rejects a funnel key over 64 chars`() {
        val result = FunnelValidation.validate(definition(funnelKey = "a".repeat(65)), existingFunnelCount = 0)
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `rejects a blank name`() {
        val result = FunnelValidation.validate(definition(name = "  "), existingFunnelCount = 0)
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `rejects a single-step funnel`() {
        val result = FunnelValidation.validate(definition(steps = listOf(step("view"))), existingFunnelCount = 0)
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `rejects more than 10 steps`() {
        val steps = (1..11).map { step("step$it") }
        val result = FunnelValidation.validate(definition(steps = steps), existingFunnelCount = 0)
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `accepts exactly 10 steps`() {
        val steps = (1..10).map { step("step$it") }
        val result = FunnelValidation.validate(definition(steps = steps), existingFunnelCount = 0)
        assertEquals(FunnelValidationResult.Valid, result)
    }

    @Test
    fun `rejects duplicate step keys`() {
        val result = FunnelValidation.validate(
            definition(steps = listOf(step("view"), step("view"))),
            existingFunnelCount = 0,
        )
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `rejects a step with a blank event name`() {
        val result = FunnelValidation.validate(
            definition(steps = listOf(step("view", event = ""), step("submit"))),
            existingFunnelCount = 0,
        )
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `rejects a step key that fails the slug pattern`() {
        val result = FunnelValidation.validate(
            definition(steps = listOf(step("View Step"), step("submit"))),
            existingFunnelCount = 0,
        )
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `rejects a window under 60 seconds`() {
        val result = FunnelValidation.validate(definition(windowSeconds = 59), existingFunnelCount = 0)
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `accepts a window of exactly 60 seconds`() {
        val result = FunnelValidation.validate(definition(windowSeconds = 60), existingFunnelCount = 0)
        assertEquals(FunnelValidationResult.Valid, result)
    }

    @Test
    fun `rejects a window over 90 days`() {
        val result = FunnelValidation.validate(definition(windowSeconds = 90L * 86400 + 1), existingFunnelCount = 0)
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `accepts a window of exactly 90 days`() {
        val result = FunnelValidation.validate(definition(windowSeconds = 90L * 86400), existingFunnelCount = 0)
        assertEquals(FunnelValidationResult.Valid, result)
    }

    @Test
    fun `rejects the 21st funnel in a project`() {
        val result = FunnelValidation.validate(definition(), existingFunnelCount = 20)
        assertIs<FunnelValidationResult.Invalid>(result)
    }

    @Test
    fun `accepts the 20th funnel in a project`() {
        val result = FunnelValidation.validate(definition(), existingFunnelCount = 19)
        assertEquals(FunnelValidationResult.Valid, result)
    }

    @Test
    fun `collects multiple errors at once rather than stopping at the first`() {
        val result = FunnelValidation.validate(
            definition(funnelKey = "Bad Key", name = "", steps = listOf(step("only-one"))),
            existingFunnelCount = 0,
        )
        val invalid = result as FunnelValidationResult.Invalid
        assert(invalid.errors.size >= 3) { "expected multiple errors, got: ${invalid.errors}" }
    }
}
