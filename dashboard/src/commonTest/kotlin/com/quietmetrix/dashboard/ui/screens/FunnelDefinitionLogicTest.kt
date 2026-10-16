package com.quietmetrix.dashboard.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FunnelDefinitionLogicTest {
    @Test fun addingAfterDeletionKeepsStepKeysUnique() {
        assertEquals("step_2", nextFunnelStepKey(setOf("step_1","step_3")))
    }
    @Test fun propertyEqualityRejectsNestedDataAndOversizedKeys() {
        assertEquals(mapOf("plan" to "free"), parseFunnelProperties("""{"plan":"free"}"""))
        assertNull(parseFunnelProperties("""{"plan":{"name":"free"}}"""))
        assertNull(parseFunnelProperties("not json"))
    }
}
