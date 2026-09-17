package com.quietmetrix.analytics.internal.counters

import kotlin.test.Test
import kotlin.test.assertEquals

class DwellBucketTest {

    @Test
    fun `boundaries land in the bucket that starts at that boundary`() {
        assertEquals("0_5s", dwellBucket(0))
        assertEquals("0_5s", dwellBucket(4_999))
        assertEquals("5_10s", dwellBucket(5_000))
        assertEquals("10_30s", dwellBucket(10_000))
        assertEquals("30_60s", dwellBucket(30_000))
        assertEquals("60_300s", dwellBucket(60_000))
        assertEquals("300s_plus", dwellBucket(300_000))
    }

    @Test
    fun `a very long dwell lands in the open-ended bucket`() {
        assertEquals("300s_plus", dwellBucket(3_600_000))
    }
}
