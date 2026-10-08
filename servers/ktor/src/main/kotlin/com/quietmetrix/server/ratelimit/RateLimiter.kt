package com.quietmetrix.server.ratelimit

import com.quietmetrix.server.config.RateLimitConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RateLimiter(private val config: RateLimitConfig, private val scope: CoroutineScope) {

    private val buckets = java.util.concurrent.ConcurrentHashMap<String, BucketEntry>()

    init {
        scope.launch {
            while (isActive) {
                delay(60_000L) // 1-hour cleanup tick
                evictStaleBuckets()
            }
        }
    }

    private data class BucketEntry(
        val bucket: TokenBucket,
        @Volatile var lastAccess: Long = System.currentTimeMillis(),
    )

    fun tryConsume(key: String): Boolean {
        if (!config.enabled) return true
        if (!buckets.containsKey(key) && buckets.size >= 10_000) {
            evictStaleBuckets()
            if (buckets.size >= 10_000) return false
        }
        val entry = buckets.computeIfAbsent(key) {
            BucketEntry(TokenBucket(config.requestsPerSecond, config.burstPerMinute))
        }
        entry.lastAccess = System.currentTimeMillis()
        return entry.bucket.tryConsume()
    }

    fun getRemaining(key: String): Int {
        val entry = buckets[key] ?: return config.burstPerMinute
        return entry.bucket.remaining()
    }

    fun getResetSeconds(key: String): Int {
        return 60
    }

    private fun evictStaleBuckets() {
        val cutoff = System.currentTimeMillis() - 5 * 60 * 1000L
        val iterator = buckets.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastAccess < cutoff) {
                iterator.remove()
            }
        }
    }

    }