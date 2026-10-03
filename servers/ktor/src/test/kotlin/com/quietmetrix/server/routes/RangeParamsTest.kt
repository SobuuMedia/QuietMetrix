package com.quietmetrix.server.routes

import kotlin.test.Test
import kotlin.test.assertEquals

/** Coverage for dashboard range resolution shared by analytics routes. */
class RangeParamsTest {
    @Test
    fun rangeTokensTakePrecedenceAndLegacyDaysRemainSupported() {
        assertEquals(3_600L, parseRangeWindowSeconds("1h", "30"))
        assertEquals(604_800L, parseRangeWindowSeconds("7d", null))
        assertEquals(2_592_000L, parseRangeWindowSeconds(null, "30"))
        assertEquals(7_776_000L, parseRangeWindowSeconds(null, "400"))
    }
}
