package com.quietmetrix.server.funnels

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One event row from the events table, pre-filtered by the caller to only events whose name
 * matches some step of the funnel, within the (widened) query range. [actorKey] is
 * pre-computed via [com.quietmetrix.server.funnels.actorKey] from that row's install hash /
 * session id.
 */
data class FunnelActorRow(
    val actorKey: String,
    val installHash: String? = null,
    val sessionId: String? = null,
    val eventName: String,
    val ts: Instant,
    val screen: String? = null,
    val props: Map<String, String> = emptyMap(),
    val country: String? = null,
    val platform: String? = null,
    val deviceClass: String? = null,
    val language: String? = null,
)

data class FunnelStepResult(
    val key: String,
    val name: String?,
    val count: Int,
    val conversionFromEntry: Double,
    val conversionFromPrevious: Double,
    val dropped: Int,
    val dropRate: Double,
    val medianMsFromPrevious: Long?,
    val p90MsFromPrevious: Long?,
)

data class FunnelBreakdownValue(
    val value: String,
    val entered: Int,
    val overallConversion: Double,
    val steps: List<FunnelStepResult>,
)

data class FunnelBreakdown(
    val dimension: String,
    val values: List<FunnelBreakdownValue>,
)

data class FunnelTrendPoint(
    val bucket: String,
    val entered: Int,
    val converted: Int,
    val conversion: Double,
)

data class FunnelResults(
    val entered: Int,
    val converted: Int,
    val overallConversion: Double,
    val medianTotalMs: Long?,
    val steps: List<FunnelStepResult>,
    val breakdown: FunnelBreakdown?,
    val trend: List<FunnelTrendPoint>?,
    /** "install" | "session" | "mixed" — see [actorKey]. */
    val countedBy: String,
)

/**
 * Aggregates raw event rows into per-step conversion, drop-off, time-to-convert, an optional
 * breakdown, and an optional trend. This is the pure half of the `/funnels/{key}/results`
 * endpoint — the route handler only runs the SQL query and maps [FunnelResults] to the wire
 * response; every number here is independently testable without a database.
 */
object FunnelAnalyzer {

    private class MatchedActor(
        val actorKey: String,
        val result: FunnelMatchResult,
        val dimensionValue: String?,
    )

    fun analyze(
        steps: List<FunnelStepDefinition>,
        windowSeconds: Long,
        rows: List<FunnelActorRow>,
        from: Instant,
        to: Instant,
        countMode: String = "actor",
        identityScope: String = "install_or_session",
        correlationProperty: String? = null,
        breakdownDimension: String? = null,
        withTrend: Boolean = false,
    ): FunnelResults {
        val byActor = rows.mapNotNull { row ->
            val identity = when (identityScope) {
                "install" -> row.installHash?.let { "install:$it" }
                "session" -> row.sessionId?.let { "sid:$it" }
                else -> row.actorKey
            } ?: return@mapNotNull null
            val attempt = if (countMode == "attempt") row.props[correlationProperty] else null
            if (countMode == "attempt" && attempt == null) return@mapNotNull null
            (if (attempt == null) identity else "$identity|attempt:$attempt") to row
        }.groupBy({ it.first }, { it.second })
        val matched = byActor.mapNotNull { (actor, actorRows) ->
            val events = actorRows.map { FunnelEvent(it.eventName, it.ts, it.screen, it.props) }
            val result = FunnelMatcher.match(steps, windowSeconds, events) ?: return@mapNotNull null
            val entryTs = result.reached.first().ts
            if (entryTs < from || entryTs >= to) return@mapNotNull null
            val dimensionValue = breakdownDimension?.let { dim ->
                // The actual entry event, not just the earliest row — a row can match the
                // entry step's event name but fail its screen/prop filter, so it is not the
                // event FunnelMatcher used as the entry.
                val entryRow = actorRows.firstOrNull { it.ts == entryTs }
                dimensionValue(entryRow, dim)
            }
            MatchedActor(actor, result, dimensionValue)
        }

        val stepResults = computeSteps(steps, matched.map { it.result })
        val entered = matched.size
        val converted = matched.count { it.result.completed }
        val overallConversion = safeDiv(converted, entered)
        val completedDeltasMs = matched
            .filter { it.result.completed }
            .map { it.result.reached.last().ts.toEpochMilliseconds() - it.result.reached.first().ts.toEpochMilliseconds() }
        val medianTotalMs = Percentile.of(completedDeltasMs, 50.0)

        val countedBy = when {
            identityScope == "session" -> "session"
            identityScope == "install" -> "install"
            matched.isEmpty() -> "install"
            matched.all { !it.actorKey.startsWith("sid:") } -> "install"
            matched.all { it.actorKey.startsWith("sid:") } -> "session"
            else -> "mixed"
        }

        val breakdown = breakdownDimension?.let { dim ->
            val values = matched.groupBy { it.dimensionValue ?: "unknown" }.map { (value, group) ->
                val groupSteps = computeSteps(steps, group.map { it.result })
                FunnelBreakdownValue(
                    value = value,
                    entered = group.size,
                    overallConversion = safeDiv(group.count { it.result.completed }, group.size),
                    steps = groupSteps,
                )
            }.sortedByDescending { it.entered }
            FunnelBreakdown(dim, values)
        }

        val trend = if (withTrend) computeTrend(matched) else null

        return FunnelResults(
            entered = entered,
            converted = converted,
            overallConversion = overallConversion,
            medianTotalMs = medianTotalMs,
            steps = stepResults,
            breakdown = breakdown,
            trend = trend,
            countedBy = if (countMode == "attempt") "$countedBy attempt" else countedBy,
        )
    }

    private fun computeSteps(steps: List<FunnelStepDefinition>, results: List<FunnelMatchResult>): List<FunnelStepResult> {
        val entered = results.size
        val counts = steps.indices.map { i -> results.count { it.reached.size > i } }
        return steps.mapIndexed { i, step ->
            val count = counts[i]
            val previousCount = if (i == 0) entered else counts[i - 1]
            val deltasMs = if (i == 0) {
                emptyList()
            } else {
                results.filter { it.reached.size > i }
                    .map { it.reached[i].ts.toEpochMilliseconds() - it.reached[i - 1].ts.toEpochMilliseconds() }
            }
            FunnelStepResult(
                key = step.key,
                name = step.name,
                count = count,
                conversionFromEntry = safeDiv(count, entered),
                // The entry step has no previous step; 0 is deliberate so clients do not
                // display a misleading "100% from previous" on an empty funnel.
                conversionFromPrevious = if (i == 0) 0.0 else safeDiv(count, previousCount),
                dropped = previousCount - count,
                dropRate = if (i == 0) 0.0 else safeDiv(previousCount - count, previousCount),
                medianMsFromPrevious = if (i == 0) null else Percentile.of(deltasMs, 50.0),
                p90MsFromPrevious = if (i == 0) null else Percentile.of(deltasMs, 90.0),
            )
        }
    }

    private fun computeTrend(matched: List<MatchedActor>): List<FunnelTrendPoint> {
        return matched.groupBy { dayBucket(it.result.reached.first().ts) }
            .toSortedMap()
            .map { (bucket, group) ->
                val converted = group.count { it.result.completed }
                FunnelTrendPoint(bucket, group.size, converted, safeDiv(converted, group.size))
            }
    }

    private fun dayBucket(ts: Instant): String {
        val local = ts.toLocalDateTime(TimeZone.UTC)
        return "%04d-%02d-%02d".format(local.year, local.monthNumber, local.dayOfMonth)
    }

    private fun dimensionValue(row: FunnelActorRow?, dimension: String): String? = when (dimension) {
        "country" -> row?.country
        "platform" -> row?.platform
        "device_class" -> row?.deviceClass
        "language" -> row?.language
        else -> null
    }

    private fun safeDiv(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator.toDouble()
}
