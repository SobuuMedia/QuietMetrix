package com.quietmetrix.analytics.internal

import kotlin.test.Test
import kotlin.test.assertTrue

class EventValidatorTest {
    @Test
    fun `valid event has no errors`() {
        val errors = EventValidator.validate("page_view", "home", mapOf("key" to "value"))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `empty name returns error`() {
        val errors = EventValidator.validate("", null, emptyMap())
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `too many props returns error`() {
        val props = (1..51).associate { it.toString() to "value" }
        val errors = EventValidator.validate("test", null, props)
        assertTrue(errors.isNotEmpty())
    }
}
