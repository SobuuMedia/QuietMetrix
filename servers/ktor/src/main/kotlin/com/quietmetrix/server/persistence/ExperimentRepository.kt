package com.quietmetrix.server.persistence

import com.quietmetrix.server.experiments.ExperimentDefinition
import com.quietmetrix.server.experiments.VariantSpec
import com.quietmetrix.server.persistence.tables.Experiments
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime

/** A stored experiment definition, decoded from its row. */
data class ExperimentRecord(
    val experimentKey: String,
    val name: String,
    val screen: String?,
    val status: String,
    val trafficPercent: Int,
    val variants: List<VariantSpec>,
    val countries: List<String>?,
    val createdAt: String,
    val updatedAt: String,
    val startedAt: String?,
    val endedAt: String?,
    val selectedVariant: String? = null,
    val mode: String = "legacy",
    val placementKey: String? = null,
    val revision: Int = 0,
    val bPercent: Int? = null,
    val languages: List<String>? = null,
    val matchMode: String = "all",
    val goalEvent: String? = null,
    val iconKey: String? = null,
)

/** Fields a [ExperimentRepository.update] call may change; a null field leaves it unchanged. */
data class ExperimentUpdate(
    val name: String? = null,
    val screen: String? = null,
    val trafficPercent: Int? = null,
    val variants: List<VariantSpec>? = null,
    val countries: List<String>? = null,
    val status: String? = null,
    val selectedVariant: String? = null,
    val expectedStatus: String? = null,
    val mode: String? = null,
    val placementKey: String? = null,
    val revision: Int? = null,
    val bPercent: Int? = null,
    val languages: List<String>? = null,
    val matchMode: String? = null,
    val goalEvent: String? = null,
    val iconKey: String? = null,
)

/**
 * Persists A/B testing experiment definitions. The server never learns which device was
 * assigned which variant — this only stores what an operator configured (see
 * [com.quietmetrix.server.experiments.ExperimentDefinition]).
 */
class ExperimentRepository(private val database: Database) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Creates a `draft` experiment. Caller must have already validated [definition]. */
    fun create(projectId: Long, definition: ExperimentDefinition): ExperimentRecord {
        val now = LocalDateTime.now()
        transaction(database) {
            Experiments.insert {
                it[this.projectId] = projectId
                it[experimentKey] = definition.experimentKey
                it[name] = definition.name
                it[screen] = definition.screen
                it[status] = "draft"
                it[trafficPercent] = definition.trafficPercent
                it[variants] = json.encodeToString(definition.variants)
                it[countries] = definition.countries?.let { c -> json.encodeToString(c) }
                it[mode] = definition.mode
                it[placementKey] = definition.placementKey
                it[revision] = definition.revision
                it[bPercent] = definition.bPercent
                it[languages] = definition.languages?.let(json::encodeToString)
                it[matchMode] = definition.matchMode
                it[goalEvent] = definition.goalEvent
                it[iconKey] = definition.iconKey
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        return findByKey(projectId, definition.experimentKey)!!
    }

    fun findByKey(projectId: Long, experimentKey: String): ExperimentRecord? = transaction(database) {
        Experiments.selectAll()
            .where { (Experiments.projectId eq projectId) and (Experiments.experimentKey eq experimentKey) }
            .limit(1)
            .map(::rowToRecord)
            .singleOrNull()
    }

    fun listForProject(projectId: Long): List<ExperimentRecord> = transaction(database) {
        Experiments.selectAll()
            .where { Experiments.projectId eq projectId }
            .orderBy(Experiments.createdAt, SortOrder.DESC)
            .map(::rowToRecord)
    }

    /** Config readers include live tests and persisted rollouts. */
    fun listActive(projectId: Long): List<ExperimentRecord> = transaction(database) {
        Experiments.selectAll()
            .where { (Experiments.projectId eq projectId) and (Experiments.status inList listOf("active", "shipped")) }
            .orderBy(Experiments.createdAt, SortOrder.DESC)
            .map(::rowToRecord)
    }

    /** Non-ended experiments (draft+active+paused) — what counts against the free-plan limit. */
    fun countNonEnded(projectId: Long): Long = transaction(database) {
        Experiments.selectAll()
            .where { (Experiments.projectId eq projectId) and (Experiments.status inList listOf("draft", "active", "paused")) }
            .count()
    }

    /**
     * Partial update. Sets `started_at` the first time [ExperimentUpdate.status] becomes
     * "active" (never overwrites an existing value), and `ended_at` when it becomes "ended".
     * Returns null if missing or the guarded prior status changed; terminal resolution preserves an existing ended_at.
     */
    fun update(projectId: Long, experimentKey: String, changes: ExperimentUpdate): ExperimentRecord? {
        val existing = findByKey(projectId, experimentKey) ?: return null
        val changed = transaction(database) {
            Experiments.update({
                (Experiments.projectId eq projectId) and (Experiments.experimentKey eq experimentKey) and
                    (Experiments.status eq (changes.expectedStatus ?: existing.status))
            }) {
                if (changes.name != null) it[name] = changes.name
                if (changes.screen != null) it[screen] = changes.screen.ifBlank { null }
                if (changes.trafficPercent != null) it[trafficPercent] = changes.trafficPercent
                if (changes.variants != null) it[variants] = json.encodeToString(changes.variants)
                if (changes.countries != null) {
                    it[countries] = changes.countries.takeIf { c -> c.isNotEmpty() }?.let { c -> json.encodeToString(c) }
                }
                if (changes.mode != null) it[mode] = changes.mode
                if (changes.placementKey != null) it[placementKey] = changes.placementKey
                if (changes.revision != null) it[revision] = changes.revision
                if (changes.bPercent != null) it[bPercent] = changes.bPercent
                if (changes.languages != null) it[languages] = changes.languages.takeIf { c -> c.isNotEmpty() }?.let(json::encodeToString)
                if (changes.matchMode != null) it[matchMode] = changes.matchMode
                if (changes.goalEvent != null) it[goalEvent] = changes.goalEvent
                if (changes.iconKey != null) it[iconKey] = changes.iconKey
                if (changes.selectedVariant != null) it[selectedVariant] = changes.selectedVariant
                if (changes.status != null) {
                    it[status] = changes.status
                    if (changes.status == "active" && existing.startedAt == null) it[startedAt] = LocalDateTime.now()
                    if (changes.status in setOf("ended", "shipped", "closed") && existing.endedAt == null) it[endedAt] = LocalDateTime.now()
                }
                it[updatedAt] = LocalDateTime.now()
            }
        }
        return if (changed > 0) findByKey(projectId, experimentKey) else null
    }

    /** Hard-deletes a `draft` experiment. Returns false if it doesn't exist or isn't a draft. */
    fun deleteDraft(projectId: Long, experimentKey: String): Boolean = transaction(database) {
        Experiments.deleteWhere(limit = null) {
            (Experiments.projectId eq projectId) and (Experiments.experimentKey eq experimentKey) and (Experiments.status eq "draft")
        } > 0
    }

    private fun rowToRecord(row: ResultRow) = ExperimentRecord(
        experimentKey = row[Experiments.experimentKey],
        name = row[Experiments.name],
        screen = row[Experiments.screen],
        status = row[Experiments.status],
        trafficPercent = row[Experiments.trafficPercent],
        variants = json.decodeFromString(row[Experiments.variants]),
        countries = row[Experiments.countries]?.let { json.decodeFromString(it) },
        createdAt = row[Experiments.createdAt].toString(),
        updatedAt = row[Experiments.updatedAt].toString(),
        startedAt = row[Experiments.startedAt]?.toString(),
        endedAt = row[Experiments.endedAt]?.toString(),
        selectedVariant = row[Experiments.selectedVariant],
        mode = row[Experiments.mode],
        placementKey = row[Experiments.placementKey],
        revision = row[Experiments.revision],
        bPercent = row[Experiments.bPercent],
        languages = row[Experiments.languages]?.let { json.decodeFromString(it) },
        matchMode = row[Experiments.matchMode],
        goalEvent = row[Experiments.goalEvent],
        iconKey = row[Experiments.iconKey],
    )
}
