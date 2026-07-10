package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.EventsQuarantine
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class QuarantineRepository(private val database: Database) {

    fun insert(
        projectId: Long,
        payloadJson: String,
        reason: String,
        detail: String?,
        clientIp: String?,
        anonymousIdHash: String?,
    ): Long {
        return transaction(database) {
            val row = EventsQuarantine.insert {
                it[EventsQuarantine.projectId] = projectId
                it[EventsQuarantine.payload] = payloadJson
                it[EventsQuarantine.quarantineReason] = reason
                it[EventsQuarantine.quarantineDetail] = detail
                it[EventsQuarantine.clientIp] = clientIp
                it[EventsQuarantine.anonymousIdHash] = anonymousIdHash
                it[EventsQuarantine.quarantinedAt] = LocalDateTime.now()
            }
            row[EventsQuarantine.id]
        }
    }

    fun list(projectId: Long, limit: Int = 50): List<Map<String, Any?>> {
        return transaction(database) {
            EventsQuarantine.selectAll()
                .where { EventsQuarantine.projectId eq projectId }
                .orderBy(EventsQuarantine.quarantinedAt, order = org.jetbrains.exposed.sql.SortOrder.DESC)
                .limit(limit)
                .map { quarantineRowToMap(it) }
        }
    }

    fun release(id: Long): Boolean {
        return transaction(database) {
            EventsQuarantine.selectAll()
                .where { EventsQuarantine.id eq id }
                .limit(1)
                .firstOrNull()
                ?.let {
                    // Delete the row; caller replays the payload via EventNormalizer + EventRepository.
                    EventsQuarantine.deleteWhere { EventsQuarantine.id eq id }
                } != null
        }
    }

    fun deleteById(id: Long): Boolean {
        return transaction(database) {
            EventsQuarantine.deleteWhere { EventsQuarantine.id eq id } > 0
        }
    }

    fun getPayload(id: Long): String? {
        return transaction(database) {
            EventsQuarantine.selectAll()
                .where { EventsQuarantine.id eq id }
                .limit(1)
                .map { it[EventsQuarantine.payload] }
                .singleOrNull()
        }
    }

    private fun quarantineRowToMap(row: ResultRow) = mapOf(
        "id" to row[EventsQuarantine.id],
        "projectId" to row[EventsQuarantine.projectId],
        "payload" to row[EventsQuarantine.payload],
        "quarantineReason" to row[EventsQuarantine.quarantineReason],
        "quarantineDetail" to row[EventsQuarantine.quarantineDetail],
        "clientIp" to row[EventsQuarantine.clientIp],
        "anonymousIdHash" to row[EventsQuarantine.anonymousIdHash],
        "quarantinedAt" to row[EventsQuarantine.quarantinedAt],
    )
}