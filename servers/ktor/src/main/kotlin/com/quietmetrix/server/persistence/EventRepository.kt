package com.quietmetrix.server.persistence

import com.quietmetrix.server.domain.Event
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.kotlinDatetime
import java.time.ZoneOffset

class EventRepository(private val database: Database) {

    fun insert(event: Event): Long {
        return org.jetbrains.exposed.sql.transactions.transaction(database) {
            com.quietmetrix.server.persistence.tables.Events.insert {
                it[projectId] = event.projectId.toLong()
                it[eventName] = event.eventName
                it[screen] = event.screen
                it[props] = event.props?.let { p ->
                    org.jetbrains.exposed.sql.json.Jsonible(p)
                }
                it[sessionId] = event.sid
                it[ts] = java.time.LocalDateTime.ofInstant(event.ts.toJavaInstant(), ZoneOffset.UTC)
                it[wasOffline] = event.wasOffline
                it[country] = event.country
                it[deviceClass] = event.deviceClass
                it[language] = event.language
                it[platform] = event.platform
                it[sdkVersion] = event.sdkVersion
                it[receivedAt] = java.time.LocalDateTime.ofInstant(
                    event.receivedAt?.toJavaInstant() ?: java.time.Instant.now(),
                    ZoneOffset.UTC
                )
            } get com.quietmetrix.server.persistence.tables.Events.id
        }
    }

    fun insertToInbox(projectId: Long, payload: String): Long {
        return org.jetbrains.exposed.sql.transactions.transaction(database) {
            com.quietmetrix.server.persistence.tables.EventsInbox.insert {
                it[this.projectId] = projectId
                it[this.payload] = org.jetbrains.exposed.sql.json.Jsonible(
                    kotlinx.serialization.json.Json.parseToJsonElement(payload)
                )
            } get com.quietmetrix.server.persistence.tables.EventsInbox.id
        }
    }

    fun findByProjectId(
        projectId: Long,
        limit: Int = 100,
        offset: Int = 0,
        eventName: String? = null,
        screen: String? = null,
        from: java.time.Instant? = null,
        to: java.time.Instant? = null,
    ): List<Event> {
        return org.jetbrains.exposed.sql.transactions.transaction(database) {
            com.quietmetrix.server.persistence.tables.Events
                .select {
                    val baseOp = Op.build { com.quietmetrix.server.persistence.tables.Events.projectId eq projectId }

                    var op = baseOp
                    eventName?.let { op = op.and(com.quietmetrix.server.persistence.tables.Events.eventName eq it) }
                    screen?.let { op = op.and(com.quietmetrix.server.persistence.tables.Events.screen eq it) }
                    from?.let {
                        op = op.and(
                            com.quietmetrix.server.persistence.tables.Events.ts.greaterEq(
                                java.time.LocalDateTime.ofInstant(it, ZoneOffset.UTC)
                            )
                        )
                    }
                    to?.let {
                        op = op.and(
                            com.quietmetrix.server.persistence.tables.Events.ts.lessEq(
                                java.time.LocalDateTime.ofInstant(it, ZoneOffset.UTC)
                            )
                        )
                    }
                    op
                }
                .orderBy(com.quietmetrix.server.persistence.tables.Events.ts, SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map { rowToEvent(it) }
        }
    }

    fun countByProjectId(projectId: Long): Long {
        return org.jetbrains.exposed.sql.transactions.transaction(database) {
            com.quietmetrix.server.persistence.tables.Events
                .select { com.quietmetrix.server.persistence.tables.Events.projectId eq projectId }
                .count()
        }
    }

    private fun rowToEvent(row: ResultRow) = Event(
        id = row[com.quietmetrix.server.persistence.tables.Events.id].toString(),
        projectId = row[com.quietmetrix.server.persistence.tables.Events.projectId].toString(),
        eventName = row[com.quietmetrix.server.persistence.tables.Events.eventName],
        screen = row[com.quietmetrix.server.persistence.tables.Events.screen],
        props = null,
        sid = row[com.quietmetrix.server.persistence.tables.Events.sessionId],
        ts = row[com.quietmetrix.server.persistence.tables.Events.ts]
            .atZone(ZoneOffset.UTC).toInstant().toKotlinInstant(),
        wasOffline = row[com.quietmetrix.server.persistence.tables.Events.wasOffline],
        country = row[com.quietmetrix.server.persistence.tables.Events.country],
        deviceClass = row[com.quietmetrix.server.persistence.tables.Events.deviceClass],
        language = row[com.quietmetrix.server.persistence.tables.Events.language],
        platform = row[com.quietmetrix.server.persistence.tables.Events.platform],
        sdkVersion = row[com.quietmetrix.server.persistence.tables.Events.sdkVersion],
        receivedAt = row[com.quietmetrix.server.persistence.tables.Events.receivedAt]
            .atZone(ZoneOffset.UTC).toInstant().toKotlinInstant(),
    )
}