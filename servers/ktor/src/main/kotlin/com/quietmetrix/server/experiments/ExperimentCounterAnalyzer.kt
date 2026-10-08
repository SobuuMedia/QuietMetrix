package com.quietmetrix.server.experiments

import com.quietmetrix.server.persistence.CounterRepository
import com.quietmetrix.server.persistence.ExperimentRecord

/** Per-variant impression/interaction counts and rate. A null field means the underlying
 *  counter cell is hidden by k-anonymity, or no data exists yet — never reported as 0. */
data class ExperimentVariantResult(
    val name: String,
    val impressions: Long?,
    val interactions: Long?,
    val rate: Double?,
)

/** A two-proportion comparison between the first ([baseline]) and second ([challenger]) variant. */
data class ExperimentComparison(
    val baseline: String,
    val challenger: String,
    val z: Double,
    val pValue: Double,
    val significant: Boolean,
    val winner: String?,
)

data class ExperimentAnalysis(val variants: List<ExperimentVariantResult>, val comparison: ExperimentComparison?)

/**
 * Turns `experiment{exp, variant, action}` counter cells into a per-variant comparison, mirrored
 * exactly by php-hosting/src/experimentCounterAnalyze.php. [cells] must already be filtered to
 * one experiment's `exp` dim and come from [CounterRepository.readCells] (already k-anonymity
 * gated), same pattern as com.quietmetrix.server.funnels.FunnelCounterAnalyzer.
 */
object ExperimentCounterAnalyzer {

    /** Minimum impressions a variant needs before it participates in [ExperimentComparison]. */
    const val MIN_SAMPLE = 30

    fun analyze(experiment: ExperimentRecord, cells: List<CounterRepository.CounterCell>): ExperimentAnalysis {
        val byVariantAction = cells
            .filter { it.dims["exp"] == experiment.experimentKey && it.dims["variant"] != null && it.dims["action"] != null }
            .groupBy { it.dims["variant"] to it.dims["action"] }
            .mapValues { (_, group) -> group.sumOf { it.n } }

        val results = experiment.variants.map { variant ->
            val impressions = byVariantAction[variant.name to "impression"]
            val interactions = byVariantAction[variant.name to "interaction"]
            val rate = if (impressions != null && interactions != null && impressions > 0) {
                interactions.toDouble() / impressions.toDouble()
            } else {
                null
            }
            ExperimentVariantResult(variant.name, impressions, interactions, rate)
        }

        val comparison = buildComparison(results)
        return ExperimentAnalysis(results, comparison)
    }

    /** V2 results are once-per-installation/revision binaries. The numerator and denominator
     * are independently k-gated before a conversion rate is released. */
    fun analyzeV2(
        experiment: ExperimentRecord,
        exposureCells: List<CounterRepository.CounterCell>,
        goalCells: List<CounterRepository.CounterCell>,
    ): ExperimentAnalysis {
        val key = experiment.experimentKey
        val revision = experiment.revision.toString()
        fun totals(cells: List<CounterRepository.CounterCell>) = cells
            .filter { it.dims["exp"] == key && it.dims["rev"] == revision }
            .associate { it.dims["variant"] to it.n }
        val exposures = totals(exposureCells)
        val goals = totals(goalCells)
        val results = experiment.variants.mapIndexed { index, variant ->
            val id = if (index == 0) "a" else "b"
            val n = exposures[id]
            val successes = goals[id]
            val failures = if (n != null && successes != null) (n - successes).coerceAtLeast(0) else null
            val rate = if (n != null && successes != null && successes >= MIN_SUCCESS_OR_FAILURE &&
                failures != null && failures >= MIN_SUCCESS_OR_FAILURE) successes.toDouble() / n.toDouble() else null
            ExperimentVariantResult(variant.name, n, successes, rate)
        }
        return ExperimentAnalysis(results, buildV2Comparison(results))
    }

    private fun buildV2Comparison(results: List<ExperimentVariantResult>): ExperimentComparison? {
        if (results.size != 2) return null
        val (baseline, challenger) = results
        if (baseline.rate == null || challenger.rate == null) return null
        val n1 = baseline.impressions ?: return null
        val n2 = challenger.impressions ?: return null
        val c1 = baseline.interactions ?: return null
        val c2 = challenger.interactions ?: return null
        if (n1 < V2_MIN_SAMPLE || n2 < V2_MIN_SAMPLE || c1 < MIN_SUCCESS_OR_FAILURE || c2 < MIN_SUCCESS_OR_FAILURE ||
            n1 - c1 < MIN_SUCCESS_OR_FAILURE || n2 - c2 < MIN_SUCCESS_OR_FAILURE) return null
        val z = ExperimentStats.twoProportionZTest(
            c1, n1, c2, n2,
        ) ?: return null
        val winner = if (z.significant) if (challenger.rate > baseline.rate) challenger.name else baseline.name else null
        return ExperimentComparison(baseline.name, challenger.name, z.z, z.pValue, z.significant, winner)
    }

    const val V2_MIN_SAMPLE = 100
    const val MIN_SUCCESS_OR_FAILURE = 5

    private fun buildComparison(results: List<ExperimentVariantResult>): ExperimentComparison? {
        if (results.size != 2) return null
        val (baseline, challenger) = results
        if (baseline.rate == null || challenger.rate == null) return null
        if ((baseline.impressions ?: 0) < MIN_SAMPLE || (challenger.impressions ?: 0) < MIN_SAMPLE) return null

        val z = ExperimentStats.twoProportionZTest(
            c1 = baseline.interactions!!, n1 = baseline.impressions!!,
            c2 = challenger.interactions!!, n2 = challenger.impressions!!,
        ) ?: return null

        val winner = if (z.significant) {
            if (challenger.rate > baseline.rate) challenger.name else baseline.name
        } else {
            null
        }
        return ExperimentComparison(baseline.name, challenger.name, z.z, z.pValue, z.significant, winner)
    }
}
