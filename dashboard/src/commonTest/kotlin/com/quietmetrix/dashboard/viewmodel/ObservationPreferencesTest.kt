package com.quietmetrix.dashboard.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class ObservationPreferencesTest {
    @Test fun midnightExpiresSnoozesOnlyInTheirProject() {
        val rows = listOf(ObservationPreference("one","exit","2026-10-15",true),ObservationPreference("one","search","2026-10-15",false))
        assertEquals(setOf("exit","search"), hiddenObservationIds(rows,"one","2026-10-15"))
        assertEquals(setOf("search"), hiddenObservationIds(rows,"one","2026-10-16"))
        assertEquals(emptySet(), hiddenObservationIds(rows,"two","2026-10-15"))
    }
}
