package com.quietmetrix.server.persistence.tables

import com.quietmetrix.server.funnels.FunnelValidation
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
    // Per-project salt used to hash install `anonymousId` values before storage in
    // `install_meta` / audit tables. Rotated on API-key regeneration (old install rows are
    // discarded at rotation). See docs/security/publishable-api-key.md — Privacy note.
    val installSalt = varchar("install_salt", 64).nullable()
    // Separate per-project salt for funnel/analytics identity (events.install_hash). Unlike
    // installSalt, this is NEVER rotated on API-key regeneration — funnel and retention history
    // must survive a key rotation. See docs/security/publishable-api-key.md — Privacy note.
    val analyticsSalt = varchar("analytics_salt", 64).nullable()
    // Stage 3 — opt-in event-name allowlist. When `strict_schema` is true, ingest rejects
    // events whose name is not in `allowed_events` (JSON array). Bounded blast radius for
    // a publishable API key. See docs/security/publishable-api-key.md.
    val strictSchema = bool("strict_schema").default(false)
    val allowedEvents = text("allowed_events").nullable()
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
    // Per-project analytics-salt hash of the install id. Never the raw `anonymousId` — see
    // docs/security/publishable-api-key.md — Privacy note.
    val installHash = varchar("install_hash", 64).nullable()
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
        // Non-unique: multiple events legitimately share (project_id, ts). This index only
        // matched V1__init.sql's non-unique index by coincidence until fixed here.
        index(false, projectId, ts)
        index(false, projectId, eventName, ts)
        index(false, projectId, installHash)
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

object InstallMeta : Table("install_meta") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    // Salted SHA-256 hex of the SDK's `anonymousId`. Never the raw id. See
    // docs/security/publishable-api-key.md — Privacy note.
    val anonymousIdHash = varchar("anonymous_id_hash", 64)
    val firstSeenAt = datetime("first_seen_at").clientDefault { LocalDateTime.now() }
    val lastSeenAt = datetime("last_seen_at").clientDefault { LocalDateTime.now() }
    val eventCount = long("event_count").default(0L)
    val revoked = bool("revoked").default(false)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(projectId, anonymousIdHash)
        index(false, projectId, revoked)
    }
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

object EventsQuarantine : Table("events_quarantine") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val payload = text("payload")  // JSON-serialised TrackEventRequest, for replay on release
    val quarantineReason = varchar("quarantine_reason", 50)
    val quarantineDetail = text("quarantine_detail").nullable()
    val clientIp = varchar("client_ip", 45).nullable()
    val anonymousIdHash = varchar("anonymous_id_hash", 64).nullable()
    val quarantinedAt = datetime("quarantined_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(id)
    init { index(false, projectId, quarantinedAt) }
}

object IngestAudit : Table("ingest_audit") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val apiKeyLast4 = varchar("api_key_last4", 4).nullable()
    val clientIp = varchar("client_ip", 45).nullable()
    val anonymousIdHash = varchar("anonymous_id_hash", 64).nullable()
    val eventName = varchar("event_name", 255)
    val disposition = varchar("disposition", 20)
    val reason = text("reason").nullable()
    val auditedAt = datetime("audited_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(id)
    init { index(false, projectId, auditedAt) }
}

object Funnels : Table("funnels") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    // Slug, unique per project — the SDK's upsert key for auto-registration.
    val funnelKey = varchar("funnel_key", 64)
    val name = varchar("name", 255)
    val description = varchar("description", 1000).nullable()
    // JSON array of step objects: [{key, event, name?, screen?, props?}, ...]. Order is
    // positional — no separate ordinal column.
    val steps = text("steps")
    val windowSeconds = long("window_seconds").default(FunnelValidation.DEFAULT_WINDOW_SECONDS)
    // 'sdk' | 'dashboard'. A dashboard edit sets locked=true, after which SDK
    // auto-registration skips the row rather than silently overwriting an analyst's edit.
    val definitionSource = varchar("source", 16).default("dashboard")
    val locked = bool("locked").default(false)
    val countMode = varchar("count_mode", 16).default("actor")
    val identityScope = varchar("identity_scope", 32).default("install_or_session")
    val correlationProperty = varchar("correlation_property", 128).nullable()
    val archivedAt = datetime("archived_at").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(projectId, funnelKey)
    }
}

/** Last accepted revision for each code-owned funnel manifest. */
object FunnelManifests : Table("funnel_manifests") {
    val projectId = long("project_id").references(Projects.id)
    val namespace = varchar("namespace", 128)
    val revision = long("revision")
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(projectId, namespace)
}

object Sessions : Table("sessions") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val sessionId = varchar("session_id", 128)
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
    }
}
