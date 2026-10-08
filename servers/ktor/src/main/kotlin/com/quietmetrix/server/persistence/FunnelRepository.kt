package com.quietmetrix.server.persistence

import com.quietmetrix.server.funnels.FunnelDefinition
import com.quietmetrix.server.funnels.FunnelStepDefinition
import com.quietmetrix.server.persistence.tables.FunnelDefinitionRevisions
import com.quietmetrix.server.persistence.tables.FunnelManifests
import com.quietmetrix.server.persistence.tables.Funnels
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime

data class FunnelRecord(
    val funnelKey: String,
    val name: String,
    val description: String?,
    val steps: List<FunnelStepDefinition>,
    val windowSeconds: Long,
    val source: String,
    val locked: Boolean,
    val countMode: String,
    val identityScope: String,
    val correlationProperty: String?,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
)

class FunnelRepository(private val database: Database) {

    private val json = Json { ignoreUnknownKeys = true }

    fun create(projectId: Long, definition: FunnelDefinition): FunnelRecord {
        val now = LocalDateTime.now()
        transaction(database) {
            Funnels.insert {
                it[this.projectId] = projectId
                it[funnelKey] = definition.funnelKey
                it[name] = definition.name
                it[description] = definition.description
                it[steps] = json.encodeToString(definition.steps)
                it[windowSeconds] = definition.windowSeconds
                it[definitionSource] = definition.source
                it[locked] = definition.locked
                it[countMode] = definition.countMode
                it[identityScope] = definition.identityScope
                it[correlationProperty] = definition.correlationProperty
                it[revision] = 1
                it[createdAt] = now
                it[updatedAt] = now
            }
            insertRevision(projectId, definition, 1)
        }
        return findByKey(projectId, definition.funnelKey)!!
    }

    fun findByKey(projectId: Long, funnelKey: String): FunnelRecord? = transaction(database) {
        Funnels.selectAll()
            .where { (Funnels.projectId eq projectId) and (Funnels.funnelKey eq funnelKey) and (Funnels.archivedAt.isNull()) }
            .limit(1)
            .map(::rowToRecord)
            .singleOrNull()
    }

    fun listActive(projectId: Long): List<FunnelRecord> = transaction(database) {
        Funnels.selectAll()
            .where { (Funnels.projectId eq projectId) and (Funnels.archivedAt.isNull()) }
            .orderBy(Funnels.createdAt)
            .map(::rowToRecord)
    }

    fun countActive(projectId: Long): Int = transaction(database) {
        Funnels.selectAll()
            .where { (Funnels.projectId eq projectId) and (Funnels.archivedAt.isNull()) }
            .count()
            .toInt()
    }

    /** Records a newer manifest revision. Equal or older revisions are safe no-ops. */
    fun acceptManifestRevision(projectId: Long, namespace: String, revision: Long): Boolean = transaction(database) {
        val current = FunnelManifests.selectAll()
            .where { (FunnelManifests.projectId eq projectId) and (FunnelManifests.namespace eq namespace) }
            .limit(1).singleOrNull()?.get(FunnelManifests.revision)
        if (current != null && revision <= current) return@transaction false
        if (current == null) {
            FunnelManifests.insert {
                it[FunnelManifests.projectId] = projectId
                it[FunnelManifests.namespace] = namespace
                it[FunnelManifests.revision] = revision
                it[updatedAt] = LocalDateTime.now()
            }
        } else {
            FunnelManifests.update({ (FunnelManifests.projectId eq projectId) and (FunnelManifests.namespace eq namespace) }) {
                it[FunnelManifests.revision] = revision
                it[updatedAt] = LocalDateTime.now()
            }
        }
        true
    }

    /**
     * Partial update: a null field leaves the stored value unchanged. [lock] is one-way —
     * pass true to lock a funnel (a dashboard edit), never to unlock it. Returns false if no
     * active funnel with this key exists.
     */
    fun update(
        projectId: Long,
        funnelKey: String,
        name: String?,
        description: String?,
        steps: List<FunnelStepDefinition>?,
        windowSeconds: Long?,
        lock: Boolean,
    ): Boolean {
        return transaction(database) {
            val existing = Funnels.selectAll().where {
                (Funnels.projectId eq projectId) and (Funnels.funnelKey eq funnelKey) and Funnels.archivedAt.isNull()
            }.singleOrNull() ?: return@transaction false
            val nextSteps = steps ?: json.decodeFromString(existing[Funnels.steps])
            val nextWindow = windowSeconds ?: existing[Funnels.windowSeconds]
            val semanticChange = nextSteps != json.decodeFromString<List<FunnelStepDefinition>>(existing[Funnels.steps]) ||
                nextWindow != existing[Funnels.windowSeconds]
            val nextRevision = existing[Funnels.revision] + if (semanticChange) 1 else 0
            Funnels.update({
                (Funnels.projectId eq projectId) and (Funnels.funnelKey eq funnelKey) and (Funnels.archivedAt.isNull())
            }) {
                if (name != null) it[Funnels.name] = name
                if (description != null) it[Funnels.description] = description
                if (steps != null) it[Funnels.steps] = json.encodeToString(steps)
                if (windowSeconds != null) it[Funnels.windowSeconds] = windowSeconds
                it[Funnels.revision] = nextRevision
                if (lock) it[Funnels.locked] = true
                it[updatedAt] = LocalDateTime.now()
            }
            if (semanticChange) insertRevision(projectId, funnelKey, nextRevision, nextSteps, nextWindow,
                existing[Funnels.countMode], existing[Funnels.identityScope], existing[Funnels.correlationProperty])
            true
        }
    }

    /** Restores an archived definition in place, preserving the unique project/key row. */
    fun restore(projectId: Long, definition: FunnelDefinition): Boolean = transaction(database) {
        val existing = Funnels.selectAll().where {
            (Funnels.projectId eq projectId) and (Funnels.funnelKey eq definition.funnelKey) and Funnels.archivedAt.isNotNull()
        }.singleOrNull() ?: return@transaction false
        val nextRevision = existing[Funnels.revision] + 1
        val changed = Funnels.update({ (Funnels.projectId eq projectId) and (Funnels.funnelKey eq definition.funnelKey) and Funnels.archivedAt.isNotNull() }) {
            it[name] = definition.name
            it[description] = definition.description
            it[steps] = json.encodeToString(definition.steps)
            it[windowSeconds] = definition.windowSeconds
            it[definitionSource] = definition.source
            it[locked] = definition.locked
            it[countMode] = definition.countMode
            it[identityScope] = definition.identityScope
            it[correlationProperty] = definition.correlationProperty
            it[revision] = nextRevision
            it[archivedAt] = null
            it[updatedAt] = LocalDateTime.now()
        } > 0
        if (changed) insertRevision(projectId, definition, nextRevision)
        changed
    }

    /** Replaces the SDK-owned fields without unlocking a dashboard-owned definition. */
    fun replaceSdkDefinition(projectId: Long, definition: FunnelDefinition): Boolean = transaction(database) {
        val existing = Funnels.selectAll().where {
            (Funnels.projectId eq projectId) and (Funnels.funnelKey eq definition.funnelKey) and Funnels.archivedAt.isNull()
        }.singleOrNull() ?: return@transaction false
        val semanticChange = json.decodeFromString<List<FunnelStepDefinition>>(existing[Funnels.steps]) != definition.steps ||
            existing[Funnels.windowSeconds] != definition.windowSeconds || existing[Funnels.countMode] != definition.countMode ||
            existing[Funnels.identityScope] != definition.identityScope || existing[Funnels.correlationProperty] != definition.correlationProperty
        val nextRevision = existing[Funnels.revision] + if (semanticChange) 1 else 0
        val changed = Funnels.update({ (Funnels.projectId eq projectId) and (Funnels.funnelKey eq definition.funnelKey) and Funnels.archivedAt.isNull() }) {
            it[name] = definition.name
            it[description] = definition.description
            it[steps] = json.encodeToString(definition.steps)
            it[windowSeconds] = definition.windowSeconds
            it[definitionSource] = definition.source
            it[locked] = definition.locked
            it[countMode] = definition.countMode
            it[identityScope] = definition.identityScope
            it[correlationProperty] = definition.correlationProperty
            it[revision] = nextRevision
            it[updatedAt] = LocalDateTime.now()
        } > 0
        if (changed && semanticChange) insertRevision(projectId, definition, nextRevision)
        changed
    }

    /** Soft-delete. Returns false if no active funnel with this key exists. */
    fun archive(projectId: Long, funnelKey: String): Boolean = transaction(database) {
        Funnels.update({
            (Funnels.projectId eq projectId) and (Funnels.funnelKey eq funnelKey) and (Funnels.archivedAt.isNull())
        }) {
            it[archivedAt] = LocalDateTime.now()
        } > 0
    }

    private fun rowToRecord(row: ResultRow) = FunnelRecord(
        funnelKey = row[Funnels.funnelKey],
        name = row[Funnels.name],
        description = row[Funnels.description],
        steps = json.decodeFromString(row[Funnels.steps]),
        windowSeconds = row[Funnels.windowSeconds],
        source = row[Funnels.definitionSource],
        locked = row[Funnels.locked],
        countMode = row[Funnels.countMode],
        identityScope = row[Funnels.identityScope],
        correlationProperty = row[Funnels.correlationProperty],
        revision = row[Funnels.revision],
        createdAt = row[Funnels.createdAt].toString(),
        updatedAt = row[Funnels.updatedAt].toString(),
    )

    private fun insertRevision(projectId: Long, definition: FunnelDefinition, revision: Int) =
        insertRevision(projectId, definition.funnelKey, revision, definition.steps, definition.windowSeconds,
            definition.countMode, definition.identityScope, definition.correlationProperty)

    private fun insertRevision(
        projectId: Long, funnelKey: String, revision: Int, steps: List<FunnelStepDefinition>, windowSeconds: Long,
        countMode: String, identityScope: String, correlationProperty: String?,
    ) {
        FunnelDefinitionRevisions.insert {
            it[FunnelDefinitionRevisions.projectId] = projectId
            it[FunnelDefinitionRevisions.funnelKey] = funnelKey
            it[FunnelDefinitionRevisions.revision] = revision
            it[FunnelDefinitionRevisions.steps] = json.encodeToString(steps)
            it[FunnelDefinitionRevisions.windowSeconds] = windowSeconds
            it[FunnelDefinitionRevisions.countMode] = countMode
            it[FunnelDefinitionRevisions.identityScope] = identityScope
            it[FunnelDefinitionRevisions.correlationProperty] = correlationProperty
            it[FunnelDefinitionRevisions.createdAt] = LocalDateTime.now()
        }
    }
}
