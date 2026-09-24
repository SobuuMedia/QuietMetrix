package com.quietmetrix.analytics.internal.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceCountryTest {

    @Test
    fun normalizeRegion_lowercaseTwoLetterCode_isUppercased() {
        assertEquals("ES", normalizeRegion("es"))
    }

    @Test
    fun normalizeRegion_alreadyUppercase_isUnchanged() {
        assertEquals("US", normalizeRegion("US"))
    }

    @Test
    fun normalizeRegion_numericRegion_isNull() {
        assertNull(normalizeRegion("419"))
    }

    @Test
    fun normalizeRegion_blank_isNull() {
        assertNull(normalizeRegion(""))
    }

    @Test
    fun normalizeRegion_null_isNull() {
        assertNull(normalizeRegion(null))
    }

    @Test
    fun normalizeRegion_wrongLength_isNull() {
        assertNull(normalizeRegion("ESP"))
        assertNull(normalizeRegion("E"))
    }
}
