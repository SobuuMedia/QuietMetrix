package com.quietmetrix.analytics.internal.counters

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionBucketTest {

    @Test
    fun `buckets a short session`() {
        assertEquals("0_10s", sessionBucket(0L))
        assertEquals("0_10s", sessionBucket(9_999L))
    }

    @Test
    fun `buckets a mid-length session`() {
        assertEquals("10_30s", sessionBucket(10_000L))
        assertEquals("30_60s", sessionBucket(45_000L))
        assertEquals("60_300s", sessionBucket(120_000L))
        assertEquals("300_900s", sessionBucket(600_000L))
    }

    @Test
    fun `buckets a long session into the open-ended top bucket`() {
        assertEquals("900s_plus", sessionBucket(900_000L))
        assertEquals("900s_plus", sessionBucket(3_600_000L))
    }
}
