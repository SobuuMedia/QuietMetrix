package com.quietmetrix.server.ingest

import com.quietmetrix.server.domain.TrackEventRequest
import kotlinx.serialization.json.JsonPrimitive

class EventValidator {

    private val maxEventNameLength = 255
    private val maxScreenLength = 255
    private val maxSidLength = 128
    private val maxPropsCount = 50
    private val maxPropValueBytes = 4096
    private val maxBatchSize = 100

    fun validate(request: TrackEventRequest): ValidationResult {
        val errors = mutableListOf<String>()

        if (request.event.isBlank()) {
            errors.add("Field 'event' is required and must be a non-empty string")
        }
        if (request.event.length > maxEventNameLength) {
            errors.add("Field 'event' exceeds maximum length of $maxEventNameLength")
        }
        if (request.screen != null && request.screen.length > maxScreenLength) {
            errors.add("Field 'screen' exceeds maximum length of $maxScreenLength")
        }
        if (request.sid != null && request.sid.length > maxSidLength) {
            errors.add("Field 'sid' exceeds maximum length of $maxSidLength")
        }
        if (request.props != null && request.props.size > maxPropsCount) {
            errors.add("Field 'props' exceeds maximum of $maxPropsCount properties")
        }
        request.props?.forEach { (key, value) ->
            if (value is JsonPrimitive && value.isString && value.content.length > maxPropValueBytes) {
                errors.add("Prop '$key' value exceeds maximum of $maxPropValueBytes bytes")
            }
        }
        if (request.sdk != null) {
            if (request.sdk.platform.isBlank()) {
                errors.add("Field 'sdk.platform' must be a non-empty string")
            }
            if (request.sdk.version.isBlank()) {
                errors.add("Field 'sdk.version' must be a non-empty string")
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    fun validateBatch(events: List<TrackEventRequest>): ValidationResult {
        if (events.isEmpty()) {
            return ValidationResult.Invalid(listOf("Field 'events' must contain at least one event"))
        }
        if (events.size > maxBatchSize) {
            return ValidationResult.Invalid(listOf("Batch exceeds maximum of $maxBatchSize events"))
        }
        events.forEachIndexed { index, event ->
            val result = validate(event)
            if (result is ValidationResult.Invalid) {
                return ValidationResult.Invalid(result.errors.map { "events[$index]: $it" })
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Stage 3 — per-project event-name allowlist (opt-in via `projects.strict_schema`).
     * Call after [validate] / [validateBatch]. An empty [allowedEvents] set is treated as
     * "not enforceable" (returns Valid) so callers can pass the configured allowlist without
     * a separate enabled check.
     */
    fun validateStrict(request: TrackEventRequest, allowedEvents: Set<String>): ValidationResult {
        if (allowedEvents.isEmpty()) return ValidationResult.Valid
        return if (request.event in allowedEvents) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(listOf("unknown_event: ${request.event}"))
        }
    }

    fun validateBatchStrict(events: List<TrackEventRequest>, allowedEvents: Set<String>): ValidationResult {
        if (allowedEvents.isEmpty()) return ValidationResult.Valid
        events.forEachIndexed { index, event ->
            val result = validateStrict(event, allowedEvents)
            if (result is ValidationResult.Invalid) {
                return ValidationResult.Invalid(result.errors.map { "events[$index]: $it" })
            }
        }
        return ValidationResult.Valid
    }
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}