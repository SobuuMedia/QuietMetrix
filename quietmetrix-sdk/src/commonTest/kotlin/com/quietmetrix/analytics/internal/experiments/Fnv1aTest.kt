package com.quietmetrix.analytics.internal.experiments

import kotlin.test.Test
import kotlin.test.assertEquals

class Fnv1aTest {

    @Test
    fun fnv1a32_emptyString() {
        assertEquals(2166136261u, fnv1a32(""))
        assertEquals(61, bucketOf(""))
    }

    @Test
    fun fnv1a32_singleCharA() {
        assertEquals(3826002220u, fnv1a32("a"))
        assertEquals(20, bucketOf("a"))
    }

    @Test
    fun fnv1a32_ctaColorEnrollBucket0001() {
        assertEquals(752850121u, fnv1a32("cta_color:enroll:qm_bkt_0001"))
        assertEquals(21, bucketOf("cta_color:enroll:qm_bkt_0001"))
    }

    @Test
    fun fnv1a32_ctaColorSplitBucket0001() {
        assertEquals(369717707u, fnv1a32("cta_color:split:qm_bkt_0001"))
        assertEquals(7, bucketOf("cta_color:split:qm_bkt_0001"))
    }

    @Test
    fun fnv1a32_ctaColorEnrollBucket0002() {
        assertEquals(702517264u, fnv1a32("cta_color:enroll:qm_bkt_0002"))
        assertEquals(64, bucketOf("cta_color:enroll:qm_bkt_0002"))
    }

    @Test
    fun fnv1a32_ctaColorSplitBucket0002() {
        assertEquals(386495326u, fnv1a32("cta_color:split:qm_bkt_0002"))
        assertEquals(26, bucketOf("cta_color:split:qm_bkt_0002"))
    }
}
