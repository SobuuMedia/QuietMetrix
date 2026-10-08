package com.quietmetrix.server.persistence

import at.favre.lib.crypto.bcrypt.BCrypt
import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Counters
import com.quietmetrix.server.persistence.tables.ProjectMembers
import com.quietmetrix.server.persistence.tables.Projects
import com.quietmetrix.server.persistence.tables.Users
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProjectRepository(private val database: Database) {
    fun ready(): Boolean = runCatching {
        (database.connector().connection as java.sql.Connection).use { jdbc ->
            val previousTimeout = jdbc.networkTimeout
            // A stalled database must not keep readiness probes and request threads waiting.
            jdbc.setNetworkTimeout(java.util.concurrent.Executor { it.run() }, 2_000)
            try {
                jdbc.createStatement().use { statement ->
                    for (sql in listOf(
                        "SELECT id FROM auth_sessions LIMIT 0",
                        "SELECT tenant_id, platform_operator FROM users LIMIT 0",
                        "SELECT segment_kind FROM counter_cells_v2 LIMIT 0",
                        "SELECT bucket_key FROM request_rate_buckets LIMIT 0",
                    )) statement.executeQuery(sql).close()
                }
            } finally {
                if (!jdbc.isClosed) jdbc.setNetworkTimeout(java.util.concurrent.Executor { it.run() }, previousTimeout)
            }
        }
    }.isSuccess


    fun findByTenant(tenantId: String, limit: Int = 50, offset: Int = 0): List<Map<String, Any?>> = transaction(database) {
        (Projects innerJoin Users).selectAll().where { (Users.tenantId eq tenantId) and Projects.deletedAt.isNull() }
            .limit(limit).offset(offset.toLong()).map(::rowToMap)
    }

    fun countTenant(tenantId: String): Long = transaction(database) {
        (Projects innerJoin Users).selectAll().where { (Users.tenantId eq tenantId) and Projects.deletedAt.isNull() }.count()
    }

    data class ActivitySummary(val lastReceivedAt: String?, val platforms: List<String>, val sdkVersions: List<String>) {
        val status: String get() = when {
            lastReceivedAt == null -> "waiting_for_first_data"
            runCatching { Instant.parse(lastReceivedAt).isAfter(Instant.now().minusSeconds(86_400)) }.getOrDefault(false) -> "receiving_data"
            else -> "no_recent_data"
        }
    }

    /** Project-card metadata comes only from real ingest rows; platform/SDK coverage is hidden below k=5. */
    fun activitySummaries(projectIds: List<Long>): Map<Long, ActivitySummary> = transaction(database) {
        if (projectIds.isEmpty()) return@transaction emptyMap()
        val v2Rows = CounterCellsV2.selectAll().where { CounterCellsV2.projectId inList projectIds }.toList()
        val legacyRows = Counters.selectAll().where { Counters.projectId inList projectIds }.toList()
        projectIds.associateWith { id ->
            val v2 = v2Rows.filter { it[CounterCellsV2.projectId] == id }
            val legacy = legacyRows.filter { it[Counters.projectId] == id }
            val latest = (v2.map { it[CounterCellsV2.updatedAt].toInstant(ZoneOffset.UTC) } +
                legacy.map { it[Counters.updatedAt].toInstant(ZoneOffset.UTC) })
                .maxOrNull()?.toString()
            val coverageGroups = v2.asSequence()
                .filter { it[CounterCellsV2.metric] == "unique_window_v2" && it[CounterCellsV2.hour] == (-1).toShort() }
                .mapNotNull { row -> runCatching {
                    val dims = kotlinx.serialization.json.Json.parseToJsonElement(row[CounterCellsV2.dims]) as kotlinx.serialization.json.JsonObject
                    if (dims["scope_kind"]?.toString() != "\"project\"" || dims["scope_key"]?.toString() != "\"all\"") return@runCatching null
                    val key = listOf("days", "end_day").map { dims[it]?.toString() ?: return@runCatching null }.joinToString("|")
                    key to row[CounterCellsV2.contributors]
                }.getOrNull() }
                .groupBy({ it.first }, { it.second })
            val coverage = coverageGroups.values.maxOfOrNull { it.sum() } ?: 0L
            val legacyCoverage = legacy.maxOfOrNull { it[Counters.devices] } ?: 0L
            val reportable = maxOf(coverage, legacyCoverage) >= 5
            ActivitySummary(
                lastReceivedAt = latest,
                platforms = if (reportable) (v2.map { it[CounterCellsV2.platform] } + legacy.map { it[Counters.platform] })
                    .filter { it in setOf("android", "ios", "web", "desktop") }.distinct().sorted() else emptyList(),
                sdkVersions = if (reportable) v2.map { it[CounterCellsV2.sdkVersion] }.filter { it != "unknown" }.distinct().sorted() else emptyList(),
            )
        }
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun create(name: String, description: String?, ownerUserId: Long, idempotencyKey: String? = null, iconKey: String = "book", declaredPlatforms: List<String> = emptyList(), projectLimit: Int? = null): String {
        val apiKey = "qm_ak_${UUID.randomUUID().toString().replace("-", "")}"
        val apiKeyHash = BCrypt.withDefaults().hashToString(12, apiKey.toCharArray())
        val apiKeySha256 = sha256(apiKey)

        transaction(database) {
            if (projectLimit != null) {
                val tenant = Users.selectAll().where { Users.id eq ownerUserId }.single()[Users.tenantId]
                Users.selectAll().where { Users.tenantId eq tenant }.orderBy(Users.id).limit(1).forUpdate().single()
                val count = (Projects innerJoin Users).selectAll().where { (Users.tenantId eq tenant) and Projects.deletedAt.isNull() }.count()
                if (count >= projectLimit) throw ProjectLimitExceeded()
            }
            Projects.insert {
                it[this.name] = name
                it[this.description] = description
                it[this.iconKey] = iconKey
                it[this.declaredPlatforms] = kotlinx.serialization.json.Json.encodeToString(declaredPlatforms)
                it[this.ownerUserId] = ownerUserId
                it[this.planId] = Users.selectAll().where { Users.id eq ownerUserId }.single()[Users.planId]
                it[this.apiKeyHash] = apiKeyHash
                it[this.apiKeySha256] = apiKeySha256
                it[this.apiKeyLast4] = apiKey.takeLast(4)
                it[this.idempotencyKey] = idempotencyKey
            }
        }
        return apiKey
    }

    /**
     * Finds a project previously created with [idempotencyKey] by [ownerUserId], if any.
     * Used to make `POST /projects` retry-safe: a lost response should re-find the earlier
     * project rather than mint a second one. Scoped per-owner so two callers can reuse the
     * same key without colliding.
     */
    fun findByOwnerAndIdempotencyKey(ownerUserId: Long, idempotencyKey: String): Map<String, Any?>? {
        return transaction(database) {
            Projects.selectAll()
                .where {
                    (Projects.ownerUserId eq ownerUserId) and
                    (Projects.idempotencyKey eq idempotencyKey) and
                    (Projects.deletedAt.isNull())
                }
                .limit(1)
                .map { rowToMap(it) }
                .singleOrNull()
        }
    }

    /**
     * Issues a fresh API key for the project, invalidating the old one. Returns
     * the new plaintext key (shown once) or null if the project does not exist.
     */
    fun regenerateApiKey(id: Long): String? {
        val apiKey = "qm_ak_${UUID.randomUUID().toString().replace("-", "")}"
        val apiKeyHash = BCrypt.withDefaults().hashToString(12, apiKey.toCharArray())
        val apiKeySha256 = sha256(apiKey)
        val updated = transaction(database) {
            Projects.update(
                where = { (Projects.id eq id) and (Projects.deletedAt.isNull()) },
                body = {
                    it[this.apiKeyHash] = apiKeyHash
                    it[this.apiKeySha256] = apiKeySha256
                    it[this.apiKeyLast4] = apiKey.takeLast(4)
                }
            )
        }
        return if (updated > 0) apiKey else null
    }

    fun findById(id: Long): Map<String, Any?>? {
        return transaction(database) {
            Projects
                .selectAll()
                .where { (Projects.id eq id) and (Projects.deletedAt.isNull()) }
                .map { rowToMap(it) }
                .singleOrNull()
        }
    }

    fun findByOwnerId(ownerUserId: Long, limit: Int = 50, offset: Int = 0): List<Map<String, Any?>> {
        return transaction(database) {
            Projects
                .selectAll()
                .where { (Projects.ownerUserId eq ownerUserId) and (Projects.deletedAt.isNull()) }
                .limit(limit).offset(offset.toLong())
                .map { rowToMap(it) }
        }
    }

    /** All non-deleted projects (admin view). */
    fun findAll(limit: Int = 50, offset: Int = 0): List<Map<String, Any?>> {
        return transaction(database) {
            Projects
                .selectAll()
                .where { Projects.deletedAt.isNull() }
                .limit(limit).offset(offset.toLong())
                .map { rowToMap(it) }
        }
    }

    fun countAll(): Long {
        return transaction(database) {
            Projects.selectAll().where { Projects.deletedAt.isNull() }.count()
        }
    }

    fun countByOwnerId(ownerUserId: Long): Long {
        return transaction(database) {
            Projects
                .selectAll()
                .where { (Projects.ownerUserId eq ownerUserId) and (Projects.deletedAt.isNull()) }
                .count()
        }
    }

    fun findAccessibleByUserId(userId: Long, limit: Int = 50, offset: Int = 0): List<Map<String, Any?>> {
        return transaction(database) {
            (Projects leftJoin ProjectMembers)
                .selectAll()
                .where {
                    (Projects.deletedAt.isNull()) and
                    ((Projects.ownerUserId eq userId) or (ProjectMembers.userId eq userId))
                }
                .withDistinct()
                .limit(limit).offset(offset.toLong())
                .map { rowToMap(it) }
        }
    }

    fun countAccessibleByUserId(userId: Long): Long {
        return transaction(database) {
            (Projects leftJoin ProjectMembers)
                .selectAll()
                .where {
                    (Projects.deletedAt.isNull()) and
                    ((Projects.ownerUserId eq userId) or (ProjectMembers.userId eq userId))
                }
                .withDistinct()
                .count()
        }
    }

    fun updateName(id: Long, name: String): Boolean {
        return transaction(database) {
            Projects.update(
                where = { (Projects.id eq id) and (Projects.deletedAt.isNull()) },
                body = { it[this.name] = name }
            ) > 0
        }
    }

    /** Erases project-linked content atomically. Historical method name retained for callers. */
    fun softDelete(id: Long): Boolean = transaction(database) {
        if (Projects.selectAll().where { Projects.id eq id }.forUpdate().empty()) return@transaction false
        val jdbc = connection.connection as java.sql.Connection
        val tables = mutableSetOf<String>()
        jdbc.metaData.getTables(null, null, null, arrayOf("TABLE")).use { result ->
            while (result.next()) tables += result.getString("TABLE_NAME").lowercase()
        }
        if ("ai_operations" in tables) {
            if ("ai_summary_cache" in tables) jdbc.prepareStatement("DELETE FROM ai_summary_cache WHERE operation_id IN (SELECT operation_id FROM ai_operations WHERE project_id IN (?,?))").use {
                it.setString(1, id.toString()); it.setString(2, "proj_$id"); it.executeUpdate()
            }
            jdbc.prepareStatement("DELETE FROM ai_operations WHERE project_id IN (?,?)").use {
                it.setString(1, id.toString()); it.setString(2, "proj_$id"); it.executeUpdate()
            }
        }
        for (table in listOf("counter_batch_receipts_v2", "counter_cells_v2", "counter_batch_receipts", "counters_quarantine", "counters",
            "funnel_definition_revisions", "funnel_manifests", "funnels", "experiments", "project_members", "event_counts_daily", "usage_counters")) {
            if (table in tables) jdbc.prepareStatement("DELETE FROM $table WHERE project_id=?").use { it.setLong(1, id); it.executeUpdate() }
        }
        Projects.deleteWhere { Projects.id eq id } == 1
    }

    fun validateApiKey(apiKey: String): Long? {
        val sha256Hash = sha256(apiKey)
        return transaction(database) {
            Projects.selectAll()
                .where { (Projects.apiKeySha256 eq sha256Hash) and (Projects.deletedAt.isNull()) }
                .limit(1)
                .map { it[Projects.id] }
                .singleOrNull()
        }
    }

    private fun rowToMap(row: ResultRow) = mapOf(
        "id" to row[Projects.id],
        "name" to row[Projects.name],
        "description" to row[Projects.description],
        "iconKey" to row[Projects.iconKey],
        "declaredPlatforms" to row[Projects.declaredPlatforms],
        "ownerUserId" to row[Projects.ownerUserId],
        "apiKeyHash" to row[Projects.apiKeyHash],
        "apiKeyLast4" to row[Projects.apiKeyLast4],
        "planId" to row[Projects.planId],
        "createdAt" to row[Projects.createdAt],
    )
}

class ProjectLimitExceeded : RuntimeException("Project limit reached")
