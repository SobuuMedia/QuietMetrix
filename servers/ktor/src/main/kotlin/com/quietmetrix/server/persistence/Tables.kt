package com.quietmetrix.server.persistence.tables

import com.quietmetrix.server.funnels.FunnelValidation
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
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
    // Optional client-supplied dedup key for POST /projects. A retry of a timed-out create
    // with the same (owner, key) finds the earlier project instead of minting a second one.
    // Null for callers that don't opt in; multiple nulls per owner do not collide.
    val idempotencyKey = varchar("idempotency_key", 128).nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val deletedAt = datetime("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(ownerUserId, idempotencyKey)
    }
}

/**
 * One counter cell: `(project, day, metric, platform, app_version, country, dims_hash) -> n`.
 * `dimsHash` is [com.quietmetrix.server.counters.CounterRegistry]'s sha256 of the canonicalized
 * dims map; `dims` stores that same map as display JSON. `devices` is a running count of
 * distinct installs that have ever contributed to this cell — no install identifier is ever
 * stored. Every read path must filter `devices >= k` (k-anonymity threshold); see
 * `CounterRepository`.
 */
object Counters : Table("counters") {
    val projectId = long("project_id").references(Projects.id)
    val day = date("day")
    val metric = varchar("metric", 64)
    val platform = varchar("platform", 20).default("")
    val appVersion = varchar("app_version", 32).default("")
    val country = char("country", 2).default("")
    val dimsHash = char("dims_hash", 64)
    val dims = text("dims")
    val n = long("n").default(0L)
    val devices = long("devices").default(0L)
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(projectId, day, metric, platform, appVersion, country, dimsHash)

    init {
        index(false, projectId, metric, day)
    }
}

/** Counter cells rejected by CounterRegistry or by the per-metric distinct-cell cardinality
 *  cap. Kept for operator review. */
object CountersQuarantine : Table("counters_quarantine") {
    val id = long("id").autoIncrement()
    val projectId = long("project_id").references(Projects.id)
    val payload = text("payload")
    val quarantineReason = varchar("quarantine_reason", 50)
    val quarantineDetail = text("quarantine_detail").nullable()
    val quarantinedAt = datetime("quarantined_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, projectId, quarantinedAt)
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

/**
 * Long-lived personal access tokens (`qm_pat_…`) used by agents/CLIs to call the API
 * without a user's password. Scoped and revocable — see AccessTokenRepository.
 */
object AccessTokens : Table("access_tokens") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val name = varchar("name", 255)
    val tokenSha256 = varchar("token_sha256", 64).uniqueIndex()
    val tokenLast4 = varchar("token_last4", 4)
    // Comma-separated scope slugs, e.g. "projects:create,projects:read".
    val scopes = varchar("scopes", 500)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val expiresAt = datetime("expires_at").nullable()
    val lastUsedAt = datetime("last_used_at").nullable()
    val revokedAt = datetime("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, revokedAt)
    }
}

