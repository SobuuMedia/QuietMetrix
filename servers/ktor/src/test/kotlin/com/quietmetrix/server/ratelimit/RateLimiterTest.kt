package com.quietmetrix.server.ratelimit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertTrue

class RateLimiterTest {

    private val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Test
    fun `allows requests when rate limiting is disabled`() {
        val config = com.quietmetrix.server.config.RateLimitConfig(
            enabled = false, requestsPerSecond = 10, burstPerMinute = 60
        )
        val limiter = RateLimiter(config, testScope)
        assertTrue(limiter.tryConsume("test"))
    }

    @Test
    fun `allows requests within burst limit`() {
        val config = com.quietmetrix.server.config.RateLimitConfig(
            enabled = true, requestsPerSecond = 100, burstPerMinute = 60
        )
        val limiter = RateLimiter(config, testScope)
        repeat(10) {
            assertTrue(limiter.tryConsume("test-key"))
        }
    }
}