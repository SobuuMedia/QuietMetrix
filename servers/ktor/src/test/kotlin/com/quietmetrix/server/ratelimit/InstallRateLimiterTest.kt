package com.quietmetrix.server.ratelimit

import com.quietmetrix.server.config.InstallRateLimitConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallRateLimiterTest {

    private val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Test
    fun `allows all when disabled`() {
        val limiter = InstallRateLimiter(
            InstallRateLimitConfig(enabled = false, requestsPerSecond = 1, burstPerMinute = 30),
            testScope,
        )
        repeat(100) { assertTrue(limiter.tryConsume("p1:abc")) }
    }

    @Test
    fun `blocks once the per-install burst is exhausted`() {
        val limiter = InstallRateLimiter(
            InstallRateLimitConfig(enabled = true, requestsPerSecond = 0, burstPerMinute = 3),
            testScope,
        )
        assertTrue(limiter.tryConsume("p1:abc"))
        assertTrue(limiter.tryConsume("p1:abc"))
        assertTrue(limiter.tryConsume("p1:abc"))
        assertFalse(limiter.tryConsume("p1:abc"))
    }

    @Test
    fun `different installs have independent buckets`() {
        val limiter = InstallRateLimiter(
            InstallRateLimitConfig(enabled = true, requestsPerSecond = 0, burstPerMinute = 2),
            testScope,
        )
        assertTrue(limiter.tryConsume("p1:abc"))
        assertTrue(limiter.tryConsume("p1:abc"))
        assertFalse(limiter.tryConsume("p1:abc"))
        // A different install in the same project is independent
        assertTrue(limiter.tryConsume("p1:def"))
        assertTrue(limiter.tryConsume("p1:def"))
        // And a different project's install is independent
        assertTrue(limiter.tryConsume("p2:abc"))
    }
}