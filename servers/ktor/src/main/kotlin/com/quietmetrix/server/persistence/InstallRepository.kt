package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.InstallMeta
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Tracks per-install abuse signals for a project. The SDK's `anonymousId` is salt-hashed
 * (see [com.quietmetrix.server.ingest.InstallIdHasher]) before it reaches this table; the
 * raw id is never stored. Implements the Stage-2 ramp-up detector: an install that sends
 * more than [rampEventThreshold] events within [rampWindowMinutes] of its first sighting is
 * auto-revoked. See docs/security/publishable-api-key.md.
 */
class InstallRepository(
    private val database: Database,
    private val rampEventThreshold: Long = 500L,
    private val rampWindowMinutes: Long = 10L,
) {

    data class InstallRecord(
        val revoked: Boolean,
        val eventCount: Long,
        val firstSeenAt: LocalDateTime,
    )

    /** Insert-or-increment for one event; auto-revokes on ramp-up violation. */
    fun upsert(projectId: Long, anonymousIdHash: String): InstallRecord =
        upsertCount(projectId, anonymousIdHash, 1L)

    /**
     * Insert-or-increment for [delta] events at once (batch ingest). Auto-revokes on ramp-up
     * violation (delta events crossing [rampEventThreshold] within [rampWindowMinutes]).
     */
    fun upsertCount(projectId: Long, anonymousIdHash: String, delta: Long): InstallRecord {
        return transaction(database) {
            val existing = InstallMeta.selectAll()
                .where {
                    (InstallMeta.projectId eq projectId) and
                        (InstallMeta.anonymousIdHash eq anonymousIdHash)
                }
                .limit(1)
                .firstOrNull()

            val now = LocalDateTime.now()
            if (existing == null) {
                val newCount = delta
                val shouldRevoke = newCount >= rampEventThreshold // first sight already over the bar
                InstallMeta.insert {
                    it[InstallMeta.projectId] = projectId
                    it[InstallMeta.anonymousIdHash] = anonymousIdHash
                    it[InstallMeta.firstSeenAt] = now
                    it[InstallMeta.lastSeenAt] = now
                    it[InstallMeta.eventCount] = newCount
                    it[InstallMeta.revoked] = shouldRevoke
                }
                InstallRecord(shouldRevoke, newCount, now)
            } else {
                val firstSeen = existing[InstallMeta.firstSeenAt]
                val newCount = existing[InstallMeta.eventCount] + delta
                val withinWindow = ChronoUnit.MINUTES.between(firstSeen, now) < rampWindowMinutes
                val shouldRevoke = (!existing[InstallMeta.revoked]) &&
                    withinWindow &&
                    newCount >= rampEventThreshold
                InstallMeta.update(
                    where = {
                        (InstallMeta.projectId eq projectId) and
                            (InstallMeta.anonymousIdHash eq anonymousIdHash)
                    },
                    body = {
                        it[InstallMeta.lastSeenAt] = now
                        it[InstallMeta.eventCount] = newCount
                        if (shouldRevoke) it[InstallMeta.revoked] = true
                    },
                )
                InstallRecord(shouldRevoke || existing[InstallMeta.revoked], newCount, firstSeen)
            }
        }
    }

    fun revoke(projectId: Long, anonymousIdHash: String): Boolean {
        return transaction(database) {
            InstallMeta.update(
                where = {
                    (InstallMeta.projectId eq projectId) and
                        (InstallMeta.anonymousIdHash eq anonymousIdHash)
                },
                body = { it[InstallMeta.revoked] = true },
            ) > 0
        }
    }

    fun listForProject(projectId: Long, limit: Int = 100): List<Map<String, Any?>> {
        return transaction(database) {
            InstallMeta.selectAll()
                .where { InstallMeta.projectId eq projectId }
                .orderBy(InstallMeta.lastSeenAt, order = org.jetbrains.exposed.sql.SortOrder.DESC)
                .limit(limit)
                .map { rowToMap(it) }
        }
    }

    fun deleteForProject(projectId: Long): Int {
        return transaction(database) {
            InstallMeta.deleteWhere { InstallMeta.projectId eq projectId }
        }
    }

    private fun rowToMap(row: ResultRow) = mapOf(
        "id" to row[InstallMeta.id],
        "projectId" to row[InstallMeta.projectId],
        "anonymousIdHash" to row[InstallMeta.anonymousIdHash],
        "firstSeenAt" to row[InstallMeta.firstSeenAt],
        "lastSeenAt" to row[InstallMeta.lastSeenAt],
        "eventCount" to row[InstallMeta.eventCount],
        "revoked" to row[InstallMeta.revoked],
    )
}