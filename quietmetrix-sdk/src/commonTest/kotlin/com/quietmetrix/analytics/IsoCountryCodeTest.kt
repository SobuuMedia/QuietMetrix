package com.quietmetrix.analytics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsoCountryCodeTest {
    @Test
    fun acceptsAssignedAlpha2CodesAndRejectsUnassignedCodes() {
        assertTrue(isIsoCountryCode("FR"))
        assertTrue(isIsoCountryCode("US"))
        assertFalse(isIsoCountryCode("ZZ"))
        assertFalse(isIsoCountryCode("FRA"))
    }
}
