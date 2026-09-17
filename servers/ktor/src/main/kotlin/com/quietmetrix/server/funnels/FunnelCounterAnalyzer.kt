package com.quietmetrix.server.funnels

/**
 * One `funnel_step` counter cell already filtered to a specific funnel key (the caller reads
 * via `CounterRepository.readCells(projectId, "funnel_step", from, to)`, which applies the
 * k-anonymity gate, and picks out cells whose `f` dim matches). [step] is the 1-based depth
 * reached; [n] is how many devices reported reaching it.
 */
data class FunnelStepCell(val step: Int, val n: Long)

/**
 * The counter-based replacement for [FunnelAnalyzer]: instead of matching a raw event trail
 * per actor, this aggregates the `funnel_step{f, rev, step}` cells the device itself already
 * reduced its progress to (see the SDK's `FunnelEvaluator`) — each device reports one counter
 * increment per new depth it reaches, so summing `n` at each depth directly gives every
 * step's count. There is no per-actor reconstruction here because there is nothing to
 * reconstruct: the trail never left the device.
 *
 * What this cannot compute, because the underlying data no longer exists server-side:
 * per-step timing ([FunnelStepResult.medianMsFromPrevious]/`p90MsFromPrevious`,
 * [FunnelResults.medianTotalMs]), a dimensional [FunnelResults.breakdown], and a
 * [FunnelResults.trend] — all null. A day-bucketed trend is a plausible future addition (the
 * `counters` table's `day` column is already there; `readCells` just collapses it today), the
 * others are gone for good under aggregate-only ingest.
 *
 * Revision note: cells are not filtered by the funnel manifest's `rev` dim here — funnels
 * currently have no per-row stored revision to filter against server-side, so a funnel
 * redefinition (different step count) blends old and new depth counts under the same step
 * index. Acceptable for now; flagged rather than silently wrong.
 */
object FunnelCounterAnalyzer {

    fun analyze(steps: List<FunnelStepDefinition>, cells: List<FunnelStepCell>, countMode: String = "actor"): FunnelResults {
        if (steps.isEmpty()) {
            return FunnelResults(
                entered = 0,
                converted = 0,
                overallConversion = 0.0,
                medianTotalMs = null,
                steps = emptyList(),
                breakdown = null,
                trend = null,
                countedBy = countedBy(countMode),
            )
        }

        val counts = IntArray(steps.size)
        for (cell in cells) {
            val index = cell.step - 1
            if (index in steps.indices) counts[index] += cell.n.toInt()
        }

        val entered = counts[0]
        val converted = counts.last()
        val stepResults = steps.mapIndexed { i, step ->
            val count = counts[i]
            val previousCount = if (i == 0) entered else counts[i - 1]
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
                medianMsFromPrevious = null,
                p90MsFromPrevious = null,
            )
        }

        return FunnelResults(
            entered = entered,
            converted = converted,
            overallConversion = safeDiv(converted, entered),
            medianTotalMs = null,
            steps = stepResults,
            breakdown = null,
            trend = null,
            countedBy = countedBy(countMode),
        )
    }

    private fun countedBy(countMode: String): String = if (countMode == "attempt") "install attempt" else "install"

    private fun safeDiv(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator.toDouble()
}
