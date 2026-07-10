package com.quietmetrix.server.ratelimit

import com.quietmetrix.server.config.InstallRateLimitConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Per-install ingest throttling (abuse defense Stage 2). Token bucket keyed by
 * `<projectId>:<anonymousIdHash>` — independent of per-IP and per-project buckets. Bounded
 * blast radius for a published API key: one noisy install cannot consume a project's shared
 * budget. See docs/security/publishable-api-key.md.
 */
class InstallRateLimiter(private val config: InstallRateLimitConfig, private val scope: CoroutineScope) {

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

    fun tryConsume(installKey: String): Boolean = tryConsume(installKey, 1)

    fun tryConsume(installKey: String, tokens: Int): Boolean {
        if (!config.enabled) return true
        if (tokens <= 0) return true
        val entry = buckets.computeIfAbsent(installKey) {
            BucketEntry(TokenBucket(config.requestsPerSecond, config.burstPerMinute))
        }
        entry.lastAccess = System.currentTimeMillis()
        return entry.bucket.tryConsume(tokens)
    }

    private fun evictStaleBuckets() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val iterator = buckets.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.lastAccess < cutoff) iterator.remove()
        }
    }
}