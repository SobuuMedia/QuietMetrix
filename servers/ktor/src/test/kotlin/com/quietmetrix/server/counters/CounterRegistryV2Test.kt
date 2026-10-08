package com.quietmetrix.server.counters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CounterRegistryV2Test {
    @Test fun `validates exact registered shapes and hashes sorted keys consistently`() {
        val first = CounterRegistryV2.validate("screen_view_v2", linkedMapOf("screen" to "home"))
        val reordered = CounterRegistryV2.validate("screen_view_v2", linkedMapOf("screen" to "home"))
        assertIs<CounterRegistryV2.Result.Accepted>(first)
        assertEquals(first, reordered)
    }

    @Test fun `rejects malformed and unknown cells`() {
        assertEquals("unknown_metric", (CounterRegistryV2.validate("unknown", emptyMap()) as CounterRegistryV2.Result.Rejected).reason)
        assertEquals("dims_mismatch", (CounterRegistryV2.validate("visit_start_v2", mapOf("screen" to "home")) as CounterRegistryV2.Result.Rejected).reason)
        assertEquals("reserved_screen", (CounterRegistryV2.validate("screen_view_v2", mapOf("screen" to "__exit__")) as CounterRegistryV2.Result.Rejected).reason)
    }

    @Test fun `validates real cohort dates and supported retention ages`() {
        val good = CounterRegistryV2.validate("retention_return_v2", mapOf("entry_day" to "2026-10-04", "age_day" to "7"))
        val badDate = CounterRegistryV2.validate("retention_return_v2", mapOf("entry_day" to "2026-99-40", "age_day" to "7"))
        val badAge = CounterRegistryV2.validate("retention_return_v2", mapOf("entry_day" to "2026-10-04", "age_day" to "2"))
        assertIs<CounterRegistryV2.Result.Accepted>(good)
        assertEquals("retention_return_invalid", (badDate as CounterRegistryV2.Result.Rejected).reason)
        assertEquals("retention_return_invalid", (badAge as CounterRegistryV2.Result.Rejected).reason)
    }
}
