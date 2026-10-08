package com.quietmetrix.server.ratelimit

import com.quietmetrix.server.persistence.tables.RequestRateBuckets
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.security.MessageDigest

/** Database-coordinated fixed windows. Keys are salted hashes, retained for at most two windows. */
class SharedRateLimiter(private val db: Database) {
    fun tryConsume(key: String, limit: Int, windowSeconds: Long = 60, now: Long = System.currentTimeMillis() / 1000): Boolean = transaction(db) {
        val bucket = MessageDigest.getInstance("SHA-256").digest("$key:${now / windowSeconds}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        RequestRateBuckets.deleteWhere { expiresAt lessEq now }
        val existing = RequestRateBuckets.selectAll().where { RequestRateBuckets.key eq bucket }.count() > 0
        if (!existing && RequestRateBuckets.selectAll().count() >= 100_000) return@transaction false
        RequestRateBuckets.upsert(RequestRateBuckets.key, onUpdateExclude = listOf(RequestRateBuckets.attempts)) {
            it[RequestRateBuckets.key] = bucket
            it[attempts] = 0
            it[expiresAt] = (now / windowSeconds + 2) * windowSeconds
        }
        RequestRateBuckets.update({ RequestRateBuckets.key eq bucket }) { it[attempts] = attempts + 1 }
        RequestRateBuckets.selectAll().where { RequestRateBuckets.key eq bucket }.single()[RequestRateBuckets.attempts] <= limit
    }
}
