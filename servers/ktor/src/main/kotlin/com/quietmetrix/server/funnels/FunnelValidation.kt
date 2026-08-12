package com.quietmetrix.server.funnels

sealed class FunnelValidationResult {
    data object Valid : FunnelValidationResult()
    data class Invalid(val errors: List<String>) : FunnelValidationResult()
}

object FunnelValidation {
    private val KEY_PATTERN = Regex("^[a-z0-9_-]{1,64}$")
    const val MIN_STEPS = 2
    const val MAX_STEPS = 10
    const val MAX_FUNNELS_PER_PROJECT = 20
    const val MIN_WINDOW_SECONDS = 60L
    const val MAX_WINDOW_SECONDS = 90L * 86_400
    const val DEFAULT_WINDOW_SECONDS = 7L * 86_400

    /**
     * [existingFunnelCount] is the project's current funnel count, EXCLUDING the one being
     * validated (0 for a brand-new funnel; the pre-update count minus one when editing).
     */
    fun validate(definition: FunnelDefinition, existingFunnelCount: Int): FunnelValidationResult {
        val errors = mutableListOf<String>()

        if (!KEY_PATTERN.matches(definition.funnelKey)) {
            errors += "funnel_key must match ^[a-z0-9_-]{1,64}$"
        }
        if (definition.name.isBlank()) {
            errors += "name must not be blank"
        }
        if (definition.steps.size < MIN_STEPS || definition.steps.size > MAX_STEPS) {
            errors += "steps must have between $MIN_STEPS and $MAX_STEPS entries"
        }
        val stepKeys = definition.steps.map { it.key }
        if (stepKeys.toSet().size != stepKeys.size) {
            errors += "step keys must be unique within a funnel"
        }
        definition.steps.forEachIndexed { index, step ->
            if (!KEY_PATTERN.matches(step.key)) {
                errors += "steps[$index].key must match ^[a-z0-9_-]{1,64}$"
            }
            if (step.event.isBlank()) {
                errors += "steps[$index].event must not be blank"
            }
        }
        if (definition.windowSeconds < MIN_WINDOW_SECONDS || definition.windowSeconds > MAX_WINDOW_SECONDS) {
            errors += "window_seconds must be between $MIN_WINDOW_SECONDS and $MAX_WINDOW_SECONDS"
        }
        if (definition.countMode !in setOf("actor", "attempt")) {
            errors += "count_mode must be actor or attempt"
        }
        if (definition.identityScope !in setOf("install_or_session", "install", "session")) {
            errors += "identity_scope must be install_or_session, install, or session"
        }
        if (definition.countMode == "attempt" && definition.correlationProperty.isNullOrBlank()) {
            errors += "correlation_property is required when count_mode is attempt"
        }
        if (existingFunnelCount >= MAX_FUNNELS_PER_PROJECT) {
            errors += "a project may have at most $MAX_FUNNELS_PER_PROJECT funnels"
        }

        return if (errors.isEmpty()) FunnelValidationResult.Valid else FunnelValidationResult.Invalid(errors)
    }
}
