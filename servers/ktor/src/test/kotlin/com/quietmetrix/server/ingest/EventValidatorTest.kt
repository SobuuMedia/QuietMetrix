package com.quietmetrix.server.ingest

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventValidatorTest {

    private val validator = EventValidator()

    @Test
    fun `valid single event passes validation`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "page_view",
            screen = "home",
            ts = "2026-04-30T12:34:56Z",
        )
        val result = validator.validate(request)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `event with all fields passes validation`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "button_click",
            screen = "settings",
            props = mapOf("button_id" to kotlinx.serialization.json.JsonPrimitive("save")),
            sid = "abc123",
            ts = "2026-04-30T12:34:56Z",
            wasOffline = false,
            sdk = com.quietmetrix.server.domain.SdkInfo("android", "0.2.0"),
            ctx = com.quietmetrix.server.domain.EventContext(
                referrer = "https://google.com",
                language = "en",
                ua = "Mozilla/5.0",
                viewport = "412x914",
            ),
        )
        val result = validator.validate(request)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `empty event name fails validation`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "",
        )
        val result = validator.validate(request)
        assertTrue(result is ValidationResult.Invalid)
        assertFalse((result as ValidationResult.Invalid).errors.isEmpty())
    }

    @Test
    fun `event name exceeding max length fails validation`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "a".repeat(256),
        )
        val result = validator.validate(request)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `screen name exceeding max length fails validation`() {
        val request = com.quietmetrix.server.domain.TrackEventRequest(
            event = "test",
            screen = "s".repeat(256),
        )
        val result = validator.validate(request)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `valid batch passes validation`() {
        val events = (1..5).map {
            com.quietmetrix.server.domain.TrackEventRequest(event = "event_$it")
        }
        val request = com.quietmetrix.server.domain.TrackBatchRequest(events = events)
        val result = validator.validateBatch(events)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `empty batch fails validation`() {
        val result = validator.validateBatch(emptyList())
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `batch exceeding max size fails validation`() {
        val events = (1..101).map {
            com.quietmetrix.server.domain.TrackEventRequest(event = "event_$it")
        }
        val result = validator.validateBatch(events)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).errors.any { it.contains("100") })
    }

    @Test
    fun `batch with one invalid event reports index`() {
        val events = listOf(
            com.quietmetrix.server.domain.TrackEventRequest(event = "valid"),
            com.quietmetrix.server.domain.TrackEventRequest(event = ""),
        )
        val result = validator.validateBatch(events)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).errors.any { it.contains("events[1]") })
    }
}