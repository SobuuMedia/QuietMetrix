package com.quietmetrix.server.ratelimit

import java.util.concurrent.locks.ReentrantLock

/**
 * Lock-protected token bucket. Refills continuously at [rate] tokens/second up to [burst].
 * Shared by [RateLimiter] (per-project) and [IpRateLimiter] (per-IP) and the per-install
 * limiter so the abuse-defense stages reuse one implementation.
 */
class TokenBucket(private val rate: Int, private val burst: Int) {
    private val lock = ReentrantLock()
    private var tokens: Double = burst.toDouble()
    private var lastRefill: Long = System.nanoTime()

    fun tryConsume(): Boolean = tryConsume(1)

    fun tryConsume(n: Int): Boolean {
        if (n <= 0) return true
        lock.lock()
        return try {
            refill()
            if (tokens >= n.toDouble()) {
                tokens -= n.toDouble()
                true
            } else false
        } finally {
            lock.unlock()
        }
    }

    fun remaining(): Int = tokens.toInt().coerceAtLeast(0)

    private fun refill() {
        val now = System.nanoTime()
        val elapsed = (now - lastRefill) / 1_000_000_000.0
        tokens = (tokens + elapsed * rate).coerceAtMost(burst.toDouble())
        lastRefill = now
    }
}