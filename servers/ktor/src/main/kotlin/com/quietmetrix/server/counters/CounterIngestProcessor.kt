package com.quietmetrix.server.counters

import com.quietmetrix.server.domain.CounterBatchRequest
import com.quietmetrix.server.domain.CounterBatchResponse
import com.quietmetrix.server.persistence.CounterRepository
import java.security.MessageDigest
import java.time.LocalDate

/**
 * The Ktor-independent request-handling logic for `POST /api/v1/counters`: clamps the
 * device-reported local day into a safe ingest window, dispatches each item to
 * [CounterRepository] (which itself validates via [CounterRegistry]), and tallies the result.
 * [com.quietmetrix.server.routes.CounterRoutes] is a thin Ktor adapter over this — auth, rate
 * limiting, and payload-size checks live there, not here.
 */
object CounterIngestProcessor {

    private const val MAX_DAY_SKEW_DAYS = 2L

    sealed class IdempotentResult {
        data class Accepted(val response: CounterBatchResponse, val replayed: Boolean) : IdempotentResult()
        object Conflict : IdempotentResult()
        object InvalidBatchId : IdempotentResult()
    }

    fun processIdempotent(
        projectId: Long,
        request: CounterBatchRequest,
        today: LocalDate,
        platform: String,
        repository: CounterRepository,
        claimToken: String,
    ): IdempotentResult {
        val batchId = request.batchId ?: return IdempotentResult.InvalidBatchId
        if (!batchId.matches(Regex("^[A-Za-z0-9_-]{16,64}$"))) return IdempotentResult.InvalidBatchId
        val day = clampDay(request.day, today)
        val appVersion = request.app?.version.orEmpty()
        val country = request.app?.country?.trim()?.uppercase()?.takeIf { it.matches(Regex("^[A-Z]{2}$")) }.orEmpty()
        val deltas = request.counters.map { item ->
            CounterRepository.CounterDelta(
                metric = item.m,
                dims = item.d,
                platform = platform,
                appVersion = appVersion,
                country = country,
                n = item.n,
                isNewDevice = item.u == 1,
            )
        }
        return when (val result = repository.upsertBatchOnce(
            projectId = projectId,
            batchId = batchId,
            payloadHash = payloadHash(request),
            claimToken = claimToken,
            day = day,
            deltas = deltas,
        )) {
            is CounterRepository.BatchResult.Applied -> IdempotentResult.Accepted(
                CounterBatchResponse(accepted = result.accepted, quarantined = result.quarantined), replayed = false,
            )
            is CounterRepository.BatchResult.Replayed -> IdempotentResult.Accepted(
                CounterBatchResponse(accepted = result.accepted, quarantined = result.quarantined), replayed = true,
            )
            CounterRepository.BatchResult.PayloadConflict -> IdempotentResult.Conflict
        }
    }

    /** Length-prefixed UTF-8 fields make this digest independent of JSON property/map order. */
    internal fun payloadHash(request: CounterBatchRequest): String {
        val canonical = buildString {
            fun field(value: String?) {
                if (value == null) append("-1:") else append(value.encodeToByteArray().size).append(':').append(value)
                append(';')
            }
            field(request.day)
            field(request.sdk?.platform)
            field(request.sdk?.version)
            field(request.app?.version)
            field(request.app?.country)
            field(request.counters.size.toString())
            request.counters.forEach { item ->
                field(item.m)
                field(item.n.toString())
                field(item.u.toString())
                field(item.d.size.toString())
                item.d.toSortedMap().forEach { (key, value) -> field(key); field(value) }
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun process(
        projectId: Long,
        request: CounterBatchRequest,
        today: LocalDate,
        platform: String,
        repository: CounterRepository,
    ): CounterBatchResponse {
        val day = clampDay(request.day, today)
        val appVersion = request.app?.version.orEmpty()
        val country = request.app?.country?.trim()?.uppercase()?.takeIf { it.matches(Regex("^[A-Z]{2}$")) }.orEmpty()

        var accepted = 0
        var quarantined = 0
        for (item in request.counters) {
            val delta = CounterRepository.CounterDelta(
                metric = item.m,
                dims = item.d,
                platform = platform,
                appVersion = appVersion,
                country = country,
                n = item.n,
                isNewDevice = item.u == 1,
            )
            when (repository.upsert(projectId, day, delta)) {
                is CounterRepository.UpsertResult.Applied -> accepted++
                is CounterRepository.UpsertResult.Quarantined -> quarantined++
            }
        }
        return CounterBatchResponse(accepted = accepted, quarantined = quarantined)
    }

    /** A malformed day string, or one outside `[today - 2, today]`, clamps to the nearest
     *  bound rather than being rejected — clock skew and brief offline queuing are normal. */
    private fun clampDay(raw: String, today: LocalDate): LocalDate {
        val parsed = runCatching { LocalDate.parse(raw) }.getOrDefault(today)
        val earliest = today.minusDays(MAX_DAY_SKEW_DAYS)
        return when {
            parsed.isAfter(today) -> today
            parsed.isBefore(earliest) -> earliest
            else -> parsed
        }
    }
}
