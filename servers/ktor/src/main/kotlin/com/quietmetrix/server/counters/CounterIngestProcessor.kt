package com.quietmetrix.server.counters

import com.quietmetrix.server.domain.CounterBatchRequest
import com.quietmetrix.server.domain.CounterBatchResponse
import com.quietmetrix.server.persistence.CounterRepository
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

    fun process(
        projectId: Long,
        request: CounterBatchRequest,
        today: LocalDate,
        platform: String,
        repository: CounterRepository,
    ): CounterBatchResponse {
        val day = clampDay(request.day, today)
        val appVersion = request.app?.version.orEmpty()
        val country = request.app?.country?.uppercase()?.take(2).orEmpty()

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
