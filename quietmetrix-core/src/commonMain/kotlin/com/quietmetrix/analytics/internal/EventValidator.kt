package com.quietmetrix.analytics.internal

internal object EventValidator {
    private const val MAX_EVENT_NAME_LENGTH = 255
    private const val MAX_PROPS_COUNT = 50
    private const val MAX_SCREEN_LENGTH = 255

    fun validate(name: String, screen: String?, props: Map<String, Any?>): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) errors.add("Event name must not be empty")
        if (name.length > MAX_EVENT_NAME_LENGTH) errors.add("Event name exceeds $MAX_EVENT_NAME_LENGTH characters")
        if (screen != null && screen.length > MAX_SCREEN_LENGTH) errors.add("Screen exceeds $MAX_SCREEN_LENGTH characters")
        if (props.size > MAX_PROPS_COUNT) errors.add("Event props exceed maximum of $MAX_PROPS_COUNT")
        return errors
    }
}
