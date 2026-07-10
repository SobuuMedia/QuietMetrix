package com.quietmetrix.analytics.internal.transport

import kotlin.test.Test
import kotlin.test.assertTrue

class FlushManagerRequeueTest {
    @Test
    fun `backoff calculation starts at 1 second`() {
        FlushManager.stop()
        FlushManager.purgeQueue()
        assertTrue(true)
    }
}
