package com.quietmetrix.server.persistence

import com.quietmetrix.server.counters.CounterRegistryV2
import com.quietmetrix.server.domain.CounterAppInfo
import com.quietmetrix.server.domain.CounterV2BatchRequest
import com.quietmetrix.server.domain.CounterV2EnvelopeRequest
import com.quietmetrix.server.domain.SdkInfo
import com.quietmetrix.server.funnels.FunnelStepCell
import com.quietmetrix.server.persistence.tables.CounterBatchReceiptsV2
import com.quietmetrix.server.persistence.tables.CounterCellsV2
import com.quietmetrix.server.persistence.tables.Projects
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.LocalDate
import java.time.LocalDateTime

/** Aggregate-only, additive protocol-v2 persistence. A batch receipt commits with all cells. */
class CounterV2Repository(private val database: Database, private val maxDistinctCellsPerMetric: Int = 500) {
    private val json = Json { encodeDefaults = true }

    private data class CellInput(
        val day: String, val hour: Int?, val metric: String, val dims: Map<String, String>,
        val segmentKind: String, val segmentValue: String, val n: Long, val contributors: Int,
    )

    data class TransitionRead(val present: Boolean, val cells: List<CounterRepository.CounterCell>)

    fun readTransitions(projectId: Long, from: LocalDate, to: LocalDate, platform: String, k: Long = 5): TransitionRead = transaction(database) {
        val selected = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and (CounterCellsV2.metric eq "screen_transition_v2") and
                (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to)
        }.toList().let { selectCounterProjection(it, platform) }.filter { row -> row[CounterCellsV2.hour] == (-1).toShort() && runCatching {
                val dims = json.decodeFromString<Map<String, String>>(row[CounterCellsV2.dims])
                dims["from"] != null && dims["to"] != null
            }.getOrDefault(false)
        }
        val grouped = selected.groupBy { row -> json.decodeFromString<Map<String, String>>(row[CounterCellsV2.dims]).let { it["from"] to it["to"] } }
        TransitionRead(selected.isNotEmpty(), grouped.mapNotNull { (pair, rows) ->
            val (fromScreen, toScreen) = pair
            val dims = json.decodeFromString<Map<String, String>>(rows.first()[CounterCellsV2.dims])
            val contributors = witnessCount("screen_transition_v2", dims, from, to, platform, rows)
            if (fromScreen == null || toScreen == null || contributors < k) null else CounterRepository.CounterCell(
                mapOf("from" to fromScreen, "to" to toScreen), rows.sumOf { it[CounterCellsV2.n] }, contributors
            )
        })
    }

    /** Revision-scoped global experiment totals, k-gated after combining platform/app partitions. */
    fun readExperimentCells(
        projectId: Long, metric: String, from: LocalDate, to: LocalDate,
        experimentKey: String, revision: Int, k: Long = 5,
    ): List<CounterRepository.CounterCell> = transaction(database) {
        val selected = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and (CounterCellsV2.metric eq metric) and
                (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to)
        }.toList().let { selectCounterProjection(it, "all") }.filter { row ->
            row[CounterCellsV2.hour] == (-1).toShort() && runCatching {
                val dims = json.decodeFromString<Map<String, String>>(row[CounterCellsV2.dims])
                dims["exp"] == experimentKey && dims["revision"] == revision.toString() && dims["variant"] in setOf("a", "b")
            }.getOrDefault(false)
        }
        selected.groupBy { json.decodeFromString<Map<String, String>>(it[CounterCellsV2.dims])["variant"] }
            .mapNotNull { (variant, rows) ->
                if (variant == null) return@mapNotNull null
                val contributors = rows.sumOf { it[CounterCellsV2.contributors] }
                if (contributors < k) return@mapNotNull null
                CounterRepository.CounterCell(
                    dims = mapOf("exp" to experimentKey, "rev" to revision.toString(), "variant" to variant),
                    n = rows.sumOf { it[CounterCellsV2.n] },
                    devices = contributors,
                )
            }
    }

    /** Reads entry-cohort funnel counts across the full allowed completion window. Each step
     * remains attached to the entry day and definition revision; small contributor groups are
     * removed before the analyzer sees any values. */
    fun readDashboardFunnelSteps(
        projectId: Long, funnelKey: String, revision: Int, fromEntryDay: LocalDate, toEntryDay: LocalDate,
        completionThrough: LocalDate = toEntryDay.plusDays(90), k: Long = 5,
    ): List<FunnelStepCell> = transaction(database) {
        val selected = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq projectId) and (CounterCellsV2.metric eq "funnel_step_v2") and
                (CounterCellsV2.day greaterEq fromEntryDay) and (CounterCellsV2.day lessEq completionThrough)
        }.toList().let { selectCounterProjection(it, "all") }.filter { row ->
            row[CounterCellsV2.hour] == (-1).toShort() && runCatching {
                val dims = json.decodeFromString<Map<String, String>>(row[CounterCellsV2.dims])
                dims["namespace"] == "dashboard" && dims["f"] == funnelKey &&
                    dims["revision"] == revision.toString() && dims["entry_day"]?.let {
                        val day = LocalDate.parse(it); day >= fromEntryDay && day <= toEntryDay
                    } == true
            }.getOrDefault(false)
        }
        selected.groupBy { json.decodeFromString<Map<String, String>>(it[CounterCellsV2.dims])["step"]?.toIntOrNull() }
            .mapNotNull { (step, rows) ->
                if (step == null) return@mapNotNull null
                val eligible = rows.groupBy { it[CounterCellsV2.dims] }.filter { (_, cells) ->
                    val dims = json.decodeFromString<Map<String, String>>(cells.first()[CounterCellsV2.dims])
                    val entry = runCatching { LocalDate.parse(dims.getValue("entry_day")) }.getOrNull() ?: return@filter false
                    witnessCount("funnel_step_v2", dims, fromEntryDay, completionThrough, "all", cells, windowDays = 1, windowEnd = entry) >= k
                }.values.flatten()
                if (eligible.isEmpty()) null else FunnelStepCell(step, eligible.sumOf { it[CounterCellsV2.n] })
            }
    }

    private fun witnessCount(metric: String, dims: Map<String, String>, from: LocalDate, to: LocalDate,
        platform: String, legacyRows: List<ResultRow>, windowDays: Int? = null, windowEnd: LocalDate = to): Long {
        val validated = CounterRegistryV2.validate(metric, dims) as? CounterRegistryV2.Result.Accepted ?: return 0
        val days = windowDays ?: (java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt() + 1)
        if (days !in 1..7 && days !in setOf(30, 90)) return legacyRows.groupBy { it[CounterCellsV2.day] to it[CounterCellsV2.hour] }.values.maxOfOrNull { group -> group.sumOf { it[CounterCellsV2.contributors] } } ?: 0L
        val rows = CounterCellsV2.selectAll().where {
            (CounterCellsV2.projectId eq legacyRows.first()[CounterCellsV2.projectId]) and
                (CounterCellsV2.metric eq "witness_window_v2") and
                (CounterCellsV2.day greaterEq from) and (CounterCellsV2.day lessEq to)
        }.toList().let { selectCounterProjection(it, platform) }.filter { row -> runCatching {
            val witness = json.decodeFromString<Map<String, String>>(row[CounterCellsV2.dims])
            witness["target_metric"] == metric && witness["target_hash"] == validated.value.hash &&
                witness["days"] == days.toString() && witness["end_day"] == windowEnd.toString()
        }.getOrDefault(false) }
        return if (rows.isNotEmpty()) rows.sumOf { it[CounterCellsV2.n] }
        else legacyRows.groupBy { it[CounterCellsV2.day] to it[CounterCellsV2.hour] }.values.maxOfOrNull { group -> group.sumOf { it[CounterCellsV2.contributors] } } ?: 0L
    }

    sealed interface Result {
        data class Accepted(val accepted: Int, val replayed: Boolean) : Result
        data object Conflict : Result
        data class Invalid(val reason: String) : Result
    }

    fun apply(projectId: Long, request: CounterV2BatchRequest, claimToken: String): Result {
        val cells = request.counters.map { CellInput(request.day, request.hour, it.m, it.d, "global", "all", it.n, it.contributors) }
        return applyCells(projectId, request.batchId, request.sdk, null, cells, claimToken, canonicalPayloadHash(request))
    }

    fun apply(projectId: Long, request: CounterV2EnvelopeRequest, claimToken: String): Result {
        if (request.schema != 2) return Result.Invalid("unsupported_schema")
        val cells = request.items.map { item ->
            val contributor = if (item.metric in setOf("unique_window_v2", "unique_hour_v2", "retention_entry_v2", "retention_return_v2", "experiment_exposure_v2", "experiment_goal_v2")) 1 else 0
            CellInput(item.day, item.hour, item.metric, item.dimensions, item.segment.kind, item.segment.value, item.n, contributor)
        }
        return applyCells(projectId, request.batchId, request.sdk, null, cells, claimToken, canonicalPayloadHash(request))
    }

    private fun applyCells(
        projectId: Long, batchId: String, sdk: SdkInfo?, app: CounterAppInfo?, cells: List<CellInput>,
        claimToken: String, payloadHash: String,
    ): Result {
        if (!batchId.matches(Regex("^[A-Za-z0-9_-]{16,64}$"))) return Result.Invalid("invalid_batch_id")
        val today = LocalDate.now(java.time.ZoneOffset.UTC)
        if ((app?.version?.length ?: 0) > 32) return Result.Invalid("invalid_app_version")
        if ((sdk?.version?.length ?: 0) > 32) return Result.Invalid("invalid_sdk_version")
        if (cells.isEmpty() || cells.size > 512) return Result.Invalid("invalid_batch_size")
        if (cells.any { it.n !in 1..1_000_000_000L || it.contributors !in 0..1 }) return Result.Invalid("invalid_counter_value")
        for (cell in cells) {
            val day = runCatching { LocalDate.parse(cell.day) }.getOrNull() ?: return Result.Invalid("invalid_day")
            if (day.toString() != cell.day) return Result.Invalid("invalid_day")
            if (day < today.minusDays(7) || day > today) return Result.Invalid("origin_day_out_of_range")
            if (cell.hour != null && cell.hour !in 0..23) return Result.Invalid("invalid_hour")
            if (!validSegment(cell.segmentKind, cell.segmentValue)) return Result.Invalid("invalid_segment")
        }
        val validations = cells.map { CounterRegistryV2.validate(it.metric, it.dims) }
        val invalid = validations.filterIsInstance<CounterRegistryV2.Result.Rejected>().firstOrNull()
        if (invalid != null) return Result.Invalid(invalid.reason)
        for (item in cells) {
            val dims = item.dims
            val day = LocalDate.parse(item.day)
            val endDay = when (item.metric) {
                "unique_window_v2", "witness_window_v2" -> dims["end_day"]
                else -> null
            }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val entryCohortWitness = item.metric == "witness_window_v2" && dims["target_metric"] == "funnel_step_v2"
            if (endDay != null && if (entryCohortWitness) (endDay > day || day > endDay.plusDays(89))
                else (endDay < day || endDay > day.plusDays(89))) return Result.Invalid("window_end_out_of_range")
            if (item.metric !in setOf("unique_window_v2", "witness_window_v2") && dims["entry_day"]?.let { entry ->
                    runCatching { LocalDate.parse(entry) }.getOrNull()?.isAfter(day) == true
                } == true) return Result.Invalid("entry_day_after_origin")
        }

        return transaction(database) {
            // Serialize dimension-budget checks across replicas and project erasure.
            Projects.selectAll().where { Projects.id eq projectId }.forUpdate().singleOrNull()
                ?: return@transaction Result.Invalid("project_unavailable")
            val now = LocalDateTime.now()
            CounterBatchReceiptsV2.deleteWhere { CounterBatchReceiptsV2.expiresAt less now }
            CounterBatchReceiptsV2.upsert(
                CounterBatchReceiptsV2.projectId, CounterBatchReceiptsV2.batchId,
                // Preserve original payload, ownership claim, and response on retry. Updating
                // only created_at is harmless and gives the upsert a portable no-op conflict arm.
                onUpdateExclude = listOf(CounterBatchReceiptsV2.payloadHash, CounterBatchReceiptsV2.claimToken, CounterBatchReceiptsV2.accepted),
            ) {
                it[CounterBatchReceiptsV2.projectId] = projectId
                it[CounterBatchReceiptsV2.batchId] = batchId
                it[CounterBatchReceiptsV2.payloadHash] = payloadHash
                it[CounterBatchReceiptsV2.claimToken] = claimToken
                it[accepted] = 0
                it[createdAt] = now
                it[expiresAt] = now.plusDays(14)
            }
            val receipt = CounterBatchReceiptsV2.selectAll().where {
                (CounterBatchReceiptsV2.projectId eq projectId) and (CounterBatchReceiptsV2.batchId eq batchId)
            }.single()
            if (receipt[CounterBatchReceiptsV2.claimToken] != claimToken) {
                return@transaction if (receipt[CounterBatchReceiptsV2.payloadHash] == payloadHash)
                    Result.Accepted(receipt[CounterBatchReceiptsV2.accepted], replayed = true) else Result.Conflict
            }

            // Bound each metric's dimension cardinality just as v1 does. Check the complete
            // batch before any cell upsert so one high-cardinality item rejects every sibling.
            val requestedByMetric = cells.groupBy { it.metric }
            for (metric in requestedByMetric.keys) {
                val existingHashes = CounterCellsV2.select(CounterCellsV2.cardinalityHash)
                    .where {
                        (CounterCellsV2.projectId eq projectId) and
                            (CounterCellsV2.metric eq metric) and CounterCellsV2.cardinalityHash.isNotNull()
                    }
                    .mapNotNull { it[CounterCellsV2.cardinalityHash]?.trim()?.takeIf(String::isNotEmpty) }
                    .toMutableSet()
                val legacyRows = CounterCellsV2.selectAll().where {
                    (CounterCellsV2.projectId eq projectId) and
                        (CounterCellsV2.metric eq metric) and CounterCellsV2.cardinalityHash.isNull()
                }.toList()
                legacyRows.forEach { row -> runCatching {
                        val dims = json.decodeFromString<Map<String, String>>(row[CounterCellsV2.dims])
                        val hash = CounterRegistryV2.cardinalityHash(metric, dims)
                        CounterCellsV2.update({
                            (CounterCellsV2.projectId eq projectId) and
                                (CounterCellsV2.day eq row[CounterCellsV2.day]) and
                                (CounterCellsV2.hour eq row[CounterCellsV2.hour]) and
                                (CounterCellsV2.platform eq row[CounterCellsV2.platform]) and
                                (CounterCellsV2.appVersion eq row[CounterCellsV2.appVersion]) and
                                (CounterCellsV2.country eq row[CounterCellsV2.country]) and
                                (CounterCellsV2.dimsHash eq row[CounterCellsV2.dimsHash]) and
                                (CounterCellsV2.segmentKind eq row[CounterCellsV2.segmentKind]) and
                                (CounterCellsV2.segmentValue eq row[CounterCellsV2.segmentValue])
                        }) { it[cardinalityHash] = hash }
                        existingHashes += hash
                    }
                }
                val requestedHashes = cells.mapIndexedNotNull { index, item ->
                    if (item.metric == metric) CounterRegistryV2.cardinalityHash(metric, item.dims) else null
                }.toSet()
                if (existingHashes.union(requestedHashes).size > maxDistinctCellsPerMetric) {
                    rollback()
                    return@transaction Result.Invalid("cardinality_cap")
                }
            }

            cells.forEachIndexed { index, item ->
                val valid = validations[index] as CounterRegistryV2.Result.Accepted
                upsert(projectId, LocalDate.parse(item.day), item.hour ?: -1, sdk, app, item, valid.value)
            }
            CounterBatchReceiptsV2.update({
                (CounterBatchReceiptsV2.projectId eq projectId) and (CounterBatchReceiptsV2.batchId eq batchId)
            }) { it[accepted] = cells.size }
            Result.Accepted(cells.size, replayed = false)
        }
    }

    private fun upsert(
        projectId: Long, day: LocalDate, hour: Int, sdk: SdkInfo?, app: CounterAppInfo?,
        item: CellInput, valid: CounterRegistryV2.Valid,
    ) {
        val platform = sdk?.platform?.takeIf { it in setOf("android", "ios", "web", "desktop") } ?: "unknown"
        val appVersion = app?.version.orEmpty().take(32)
        val rawCountry = app?.country?.trim()?.uppercase().orEmpty()
        val country = if (rawCountry.matches(Regex("^[A-Z]{2}$"))) rawCountry else "  "
        CounterCellsV2.upsert(
            CounterCellsV2.projectId, CounterCellsV2.day, CounterCellsV2.hour, CounterCellsV2.metric,
            CounterCellsV2.platform, CounterCellsV2.appVersion, CounterCellsV2.country, CounterCellsV2.dimsHash,
            CounterCellsV2.segmentKind, CounterCellsV2.segmentValue,
            onUpdate = { statement ->
                statement[CounterCellsV2.n] = CounterCellsV2.n + insertValue(CounterCellsV2.n)
                statement[CounterCellsV2.contributors] = CounterCellsV2.contributors + insertValue(CounterCellsV2.contributors)
                statement[CounterCellsV2.sdkVersion] = insertValue(CounterCellsV2.sdkVersion)
                statement[CounterCellsV2.updatedAt] = insertValue(CounterCellsV2.updatedAt)
            },
        ) {
            it[CounterCellsV2.projectId] = projectId
            it[CounterCellsV2.day] = day
            it[CounterCellsV2.hour] = hour.toShort()
            it[CounterCellsV2.metric] = item.metric
            it[CounterCellsV2.platform] = platform
            it[CounterCellsV2.sdkVersion] = sdk?.version?.take(32) ?: "unknown"
            it[CounterCellsV2.appVersion] = appVersion
            it[CounterCellsV2.country] = country
            it[CounterCellsV2.segmentKind] = item.segmentKind
            it[CounterCellsV2.segmentValue] = item.segmentValue
            it[CounterCellsV2.dimsHash] = valid.hash
            it[CounterCellsV2.cardinalityHash] = CounterRegistryV2.cardinalityHash(item.metric, item.dims)
            it[CounterCellsV2.dims] = json.encodeToString<Map<String, String>>(item.dims.toSortedMap().toMap())
            it[CounterCellsV2.n] = item.n
            it[CounterCellsV2.contributors] = item.contributors.toLong()
            it[CounterCellsV2.updatedAt] = LocalDateTime.now()
        }
    }

    /** Length-prefixed canonical envelope; item order cannot alter the receipt hash. */
    internal fun canonicalPayloadHash(request: CounterV2BatchRequest): String {
        val canonical = buildString {
            fun field(value: String?) {
                if (value == null) append("-1:") else append(value.encodeToByteArray().size).append(':').append(value)
                append(';')
            }
            field(request.day); field(request.hour?.toString()); field(request.sdk?.platform); field(request.sdk?.version)
            field(request.app?.version); field(request.app?.country)
            val items = request.counters.map { it to CounterRegistryV2.validate(it.m, it.d) }
                .sortedWith(compareBy({ it.first.m }, { (it.second as? CounterRegistryV2.Result.Accepted)?.value?.canonicalDims.orEmpty() }, { it.first.n }, { it.first.contributors }))
            field(items.size.toString())
            items.forEach { (item, _) ->
                field(item.m); field(item.n.toString()); field(item.contributors.toString())
                val dims = item.d.toSortedMap(); field(dims.size.toString()); dims.forEach { (k, v) -> field(k); field(v) }
            }
        }
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    internal fun canonicalPayloadHash(request: CounterV2EnvelopeRequest): String {
        val canonical = buildString {
            fun field(value: String?) {
                if (value == null) append("-1:") else append(value.encodeToByteArray().size).append(':').append(value)
                append(';')
            }
            field(request.schema.toString()); field(request.sdk.platform); field(request.sdk.version)
            field(null); field(null)
            val items = request.items.map { item ->
                val dims = CounterRegistryV2.validate(item.metric, item.dimensions)
                Triple(item, (dims as? CounterRegistryV2.Result.Accepted)?.value?.canonicalDims.orEmpty(), item.dimensions.toSortedMap())
            }.sortedWith(compareBy({ it.first.day }, { it.first.hour ?: -1 }, { it.first.metric }, { it.first.segment.kind }, { it.first.segment.value }, { it.second }, { it.first.n }))
            field(items.size.toString())
            items.forEach { (item, _, dimensions) ->
                field(item.day); field(item.hour?.toString()); field(item.metric)
                field(item.segment.kind); field(item.segment.value); field(item.n.toString()); field(dimensions.size.toString())
                dimensions.forEach { (key, value) -> field(key); field(value) }
            }
        }
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun validSegment(kind: String, value: String): Boolean = when (kind) {
        "global" -> value == "all"
        "platform" -> value in setOf("android", "ios", "web", "desktop", "unknown")
        "country" -> value == "unknown" || java.util.Locale.getISOCountries().contains(value)
        "device_class" -> value in setOf("phone", "tablet", "desktop", "unknown")
        else -> false
    }
}
