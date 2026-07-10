package com.quietmetrix.server.persistence

import com.quietmetrix.server.persistence.tables.IngestAudit
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class IngestAuditRepository(private val database: Database) {

    fun log(
        projectId: Long,
        apiKeyLast4: String?,
        clientIp: String?,
        anonymousIdHash: String?,
        eventName: String,
        disposition: String,
        reason: String?,
    ) {
        transaction(database) {
            IngestAudit.insert {
                it[IngestAudit.projectId] = projectId
                it[IngestAudit.apiKeyLast4] = apiKeyLast4
                it[IngestAudit.clientIp] = clientIp
                it[IngestAudit.anonymousIdHash] = anonymousIdHash
                it[IngestAudit.eventName] = eventName
                it[IngestAudit.disposition] = disposition
                it[IngestAudit.reason] = reason
                it[IngestAudit.auditedAt] = LocalDateTime.now()
            }
        }
    }
}