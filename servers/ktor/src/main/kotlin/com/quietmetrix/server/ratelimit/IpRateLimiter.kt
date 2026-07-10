package com.quietmetrix.server.ratelimit

import com.quietmetrix.server.config.IpRateLimitConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Per-IP ingest throttling. Token bucket keyed by the trusted-proxy-validated client IP,
 * independent of [RateLimiter]'s per-project bucket. Bounded blast radius for a published
 * (write-only, project-scoped) API key: a single host cannot saturate a project's shared
 * bucket. See docs/security/publishable-api-key.md (Stage 1).
 */
class IpRateLimiter(private val config: IpRateLimitConfig, private val scope: CoroutineScope) {

    private val buckets = java.util.concurrent.ConcurrentHashMap<String, BucketEntry>()

    init {
        scope.launch {
            while (isActive) {
                delay(3600_000L)
                evictStaleBuckets()
            }
        }
    }

    private data class BucketEntry(
        val bucket: TokenBucket,
        @Volatile var lastAccess: Long = System.currentTimeMillis(),
    )

    fun tryConsume(ip: String): Boolean {
        if (!config.enabled) return true
        val entry = buckets.computeIfAbsent(ip) {
            BucketEntry(TokenBucket(config.requestsPerSecond, config.burstPerMinute))
        }
        entry.lastAccess = System.currentTimeMillis()
        return entry.bucket.tryConsume()
    }

    fun remaining(ip: String): Int = buckets[ip]?.bucket?.remaining() ?: config.burstPerMinute

    private fun evictStaleBuckets() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val iterator = buckets.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.lastAccess < cutoff) iterator.remove()
        }
    }
}