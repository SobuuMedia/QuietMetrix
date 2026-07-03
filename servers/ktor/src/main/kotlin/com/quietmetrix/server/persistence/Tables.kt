package com.quietmetrix.server.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val planId = varchar("plan_id", 50).nullable()
    // Global role: admin | developer | reviewer. Defaults to admin so the
    // bootstrap/first user keeps full access.
    val role = varchar("role", 20).default("admin")
    // Account lifecycle: active | invited. Invited users have no usable password
    // until they accept the invite and set one.
    val status = varchar("status", 20).default("active")
    val inviteToken = varchar("invite_token", 64).nullable()
    val inviteExpiresAt = datetime("invite_expires_at").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}

object Projects : Table("projects") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val ownerUserId = long("owner_user_id").references(Users.id)
    val apiKeyHash = varchar("api_key_hash", 255).index()
    val apiKeySha256 = varchar("api_key_sha256", 64).nullable().index()
    // Non-sensitive last 4 chars of the plaintext key, for masked display in the
    // dashboard. The full key is never recoverable (only hashes are stored).
    val apiKeyLast4 = varchar("api_key_last4", 4).nullable()
    val planId = varchar("plan_id", 50).nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val deletedAt = datetime("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object EventsInbox : Table("events_inbox") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val payload = text("payload")
    val receivedAt = datetime("received_at").clientDefault { LocalDateTime.now() }
    val processed = bool("processed").default(false)

    override val primaryKey = PrimaryKey(id)
}

object Events : Table("events") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val eventName = varchar("event_name", 255)
    val screen = varchar("screen", 255).nullable()
    val props = text("props").nullable()
    val sessionId = varchar("session_id", 128).nullable()
    val ts = datetime("ts").clientDefault { LocalDateTime.now() }
    val wasOffline = bool("was_offline").default(false)
    val country = char("country", 2).nullable()
    val deviceClass = varchar("device_class", 20).nullable()
    val language = varchar("language", 10).nullable()
    val platform = varchar("platform", 20).nullable()
    val sdkVersion = varchar("sdk_version", 20).nullable()
    val receivedAt = datetime("received_at").clientDefault { LocalDateTime.now() }
    val anonymousId = varchar("anonymous_id", 128).nullable()
    val city = varchar("city", 100).nullable()
    val region = varchar("region", 100).nullable()
    val browser = varchar("browser", 50).nullable()
    val browserVersion = varchar("browser_version", 50).nullable()
    val eventOs = varchar("os", 50).nullable()
    val osVersion = varchar("os_version", 50).nullable()
    val screenWidth = integer("screen_width").nullable()
    val screenHeight = integer("screen_height").nullable()
    val durationMs = long("duration_ms").nullable()
    val referrer = varchar("referrer", 500).nullable()
    val sessionNumber = integer("session_number").nullable()
    val isSessionStart = bool("is_session_start").default(false)
    val isSessionEnd = bool("is_session_end").default(false)

    override val primaryKey = PrimaryKey(id)

    init {
        index(true, projectId, ts)
        index(false, projectId, eventName, ts)
    }
}

object EventCountsDaily : Table("event_counts_daily") {
    val projectId = long("project_id").references(Projects.id)
    val day = date("day").clientDefault { LocalDate.now() }
    val eventName = varchar("event_name", 255)
    val count = long("count").default(0L)

    override val primaryKey = PrimaryKey(projectId, day, eventName)
}

object UsageCounters : Table("usage_counters") {
    val projectId = long("project_id").references(Projects.id)
    val period = varchar("period", 6)
    val eventsCount = long("events_count").default(0L)

    override val primaryKey = PrimaryKey(projectId, period)
}

object ProjectMembers : Table("project_members") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val userId = long("user_id").references(Users.id)
    val role = varchar("role", 20).default("viewer")
    val createdAt = datetime("created_at").clientDefault { java.time.LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(projectId, userId)
    }
}

object Sessions : Table("sessions") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val sessionId = varchar("session_id", 128)
    val anonymousId = varchar("anonymous_id", 128).nullable()
    val userId = varchar("user_id", 128).nullable()
    val startedAt = datetime("started_at").clientDefault { LocalDateTime.now() }
    val endedAt = datetime("ended_at").nullable()
    val durationSec = integer("duration_sec").nullable()
    val eventCount = integer("event_count").default(0)
    val country = char("country", 2).nullable()
    val city = varchar("city", 100).nullable()
    val region = varchar("region", 100).nullable()
    val deviceClass = varchar("device_class", 20).nullable()
    val browser = varchar("browser", 50).nullable()
    val browserVersion = varchar("browser_version", 50).nullable()
    val sessionOs = varchar("os", 50).nullable()
    val sessionOsVersion = varchar("os_version", 50).nullable()
    val platform = varchar("platform", 20).nullable()
    val screenWidth = integer("screen_width").nullable()
    val screenHeight = integer("screen_height").nullable()
    val language = varchar("language", 10).nullable()
    val referrer = varchar("referrer", 500).nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, projectId, sessionId)
        index(false, projectId, startedAt)
        index(false, anonymousId)
    }
}