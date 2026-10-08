package com.quietmetrix.server.ai

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Configuration and conservative accounting contract. */
class AiPolicyTest {
    @Test fun `AI needs explicit model and credentials or local mode`() {
        assertFalse(AiConfig().enabled(false))
        assertFalse(AiConfig(model = "local", baseUrl = "http://localhost:1234/v1").enabled(false))
        assertTrue(AiConfig(model = "local", baseUrl = "http://localhost:1234/v1", local = true).enabled(false))
        assertFalse(AiConfig(model = "x", apiKey = "secret").enabled(true))
    }
    @Test fun `UTC calendar rollover and cached input rates`() {
        assertEquals("2027-01-01", aiResetDate(LocalDate.parse("2026-12-31")))
        assertEquals(4L, AiRates(1, 1, 1, 1).reservation)
        assertEquals(125L, AiRates(100, 200, 50, 125).cost(AiUsage(1000000, 0, 0, 1000000)))
        assertEquals(75L, AiRates(100, 200, 50).cost(AiUsage(1000000, 0, 500000)))
    }
}
