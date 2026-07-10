package com.quietmetrix.server.ingest

import com.quietmetrix.server.domain.TrackEventRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventValidatorStrictTest {

    private val validator = EventValidator()

    @Test
    fun `validateStrict accepts an allowed event name`() {
        val r = TrackEventRequest(event = "page_view")
        val result = validator.validateStrict(r, setOf("page_view", "click"))
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `validateStrict rejects an unknown event name`() {
        val r = TrackEventRequest(event = "pwned")
        val result = validator.validateStrict(r, setOf("page_view", "click"))
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).errors.any { it.contains("unknown_event") })
        assertTrue((result).errors.any { it.contains("pwned") })
    }

    @Test
    fun `validateStrict allows everything when the allowlist is empty`() {
        // An empty allowlist means "no strict enforcement" callers should skip; but if called,
        // it must not reject everything. Treat empty set as "not enforceable" → Valid.
        val r = TrackEventRequest(event = "anything")
        val result = validator.validateStrict(r, emptySet())
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `validateBatchStrict reports the offending event index`() {
        val events = listOf(
            TrackEventRequest(event = "page_view"),
            TrackEventRequest(event = "pwned"),
        )
        val result = validator.validateBatchStrict(events, setOf("page_view", "click"))
        assertTrue(result is ValidationResult.Invalid)
        val errs = (result as ValidationResult.Invalid).errors
        assertTrue(errs.any { it.startsWith("events[1]:") && it.contains("unknown_event") })
    }
}