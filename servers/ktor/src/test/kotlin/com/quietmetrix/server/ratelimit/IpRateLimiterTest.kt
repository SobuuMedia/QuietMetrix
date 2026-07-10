package com.quietmetrix.server.ratelimit

import com.quietmetrix.server.config.IpRateLimitConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpRateLimiterTest {

    private val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Test
    fun `allows all requests when disabled`() {
        val limiter = IpRateLimiter(
            IpRateLimitConfig(enabled = false, requestsPerSecond = 5, burstPerMinute = 60),
            testScope,
        )
        repeat(100) { assertTrue(limiter.tryConsume("1.2.3.4"), "disabled limiter must allow all") }
    }

    @Test
    fun `blocks requests once the per-ip burst is exhausted`() {
        // requestsPerSecond = 0 → no refill between calls, so the test is deterministic.
        val limiter = IpRateLimiter(
            IpRateLimitConfig(enabled = true, requestsPerSecond = 0, burstPerMinute = 3),
            testScope,
        )
        assertTrue(limiter.tryConsume("10.0.0.1"))
        assertTrue(limiter.tryConsume("10.0.0.1"))
        assertTrue(limiter.tryConsume("10.0.0.1"))
        assertFalse(limiter.tryConsume("10.0.0.1"), "fourth request from same IP must be rejected")
    }

    @Test
    fun `different ips have independent buckets`() {
        val limiter = IpRateLimiter(
            IpRateLimitConfig(enabled = true, requestsPerSecond = 0, burstPerMinute = 2),
            testScope,
        )
        assertTrue(limiter.tryConsume("10.0.0.1"))
        assertTrue(limiter.tryConsume("10.0.0.1"))
        assertFalse(limiter.tryConsume("10.0.0.1"))

        // A different IP has its own bucket
        assertTrue(limiter.tryConsume("10.0.0.2"))
        assertTrue(limiter.tryConsume("10.0.0.2"))
        assertFalse(limiter.tryConsume("10.0.0.2"))
    }

    @Test
    fun `remaining reports burst when ip unseen`() {
        val limiter = IpRateLimiter(
            IpRateLimitConfig(enabled = true, requestsPerSecond = 0, burstPerMinute = 5),
            testScope,
        )
        assertTrue(limiter.remaining("unseen-ip") == 5)
    }
}