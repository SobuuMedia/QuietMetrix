package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.ExperimentComparisonDto
import com.quietmetrix.dashboard.api.ExperimentDto
import kotlin.math.round

private val EXPERIMENT_KEY_REGEX = Regex("^[a-z0-9_]{1,32}$")
private val VARIANT_NAME_REGEX = Regex("^[a-z0-9_]{1,32}$")
private val COUNTRY_REGEX = Regex("^[A-Za-z]{2}$")

/** Picks the experiment matching [selectedKey], or the first one if none selected/found. */
internal fun currentExperiment(experiments: List<ExperimentDto>, selectedKey: String?): ExperimentDto? =
    experiments.firstOrNull { it.key == selectedKey } ?: experiments.firstOrNull()

/** Mirrors the server's key-slug rule (ExperimentValidation.kt), for instant editor feedback. */
internal fun isValidExperimentKey(key: String): Boolean = EXPERIMENT_KEY_REGEX.matches(key)

/** Mirrors the server's variant-name rule — also rejects "none", the reserved not-enrolled value. */
internal fun isValidVariantName(name: String): Boolean = VARIANT_NAME_REGEX.matches(name) && name != "none"

/** The parsed result of a free-text country-code field. [normalized] is uppercase, deduped,
 *  sorted; [invalidTokens] are the raw tokens that weren't a 2-letter code, for inline error text. */
internal data class CountryParseResult(val normalized: List<String>, val invalidTokens: List<String>)

/** Accepts comma- and/or whitespace-separated codes, e.g. `"es, FR  it"`. A blank [text]
 *  parses to an empty, fully valid result (meaning "all countries"). */
internal fun parseCountryList(text: String): CountryParseResult {
    val tokens = text.split(',', ' ', '\n', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val normalized = mutableListOf<String>()
    val invalid = mutableListOf<String>()
    for (token in tokens) {
        if (COUNTRY_REGEX.matches(token)) {
            val upper = token.uppercase()
            if (upper !in normalized) normalized += upper
        } else {
            invalid += token
        }
    }
    return CountryParseResult(normalized.sorted(), invalid)
}

/**
 * True when the experiment-creation form has enough to submit: a valid key, a non-blank name,
 * two distinct valid variant names, a split in `[1, 99]`, a traffic percentage in `[1, 100]`,
 * and a country field with no unparseable tokens. The server re-validates fully — this only
 * gates the submit button so an obviously-incomplete form can't even be sent.
 */
internal fun isExperimentFormValid(
    key: String,
    name: String,
    variantAName: String,
    variantBName: String,
    splitPercentToA: Int,
    trafficPercent: Int,
    countriesText: String,
): Boolean {
    if (!isValidExperimentKey(key)) return false
    if (name.isBlank()) return false
    if (!isValidVariantName(variantAName) || !isValidVariantName(variantBName)) return false
    if (variantAName == variantBName) return false
    if (splitPercentToA !in 1..99) return false
    if (trafficPercent !in 1..100) return false
    if (parseCountryList(countriesText).invalidTokens.isNotEmpty()) return false
    return true
}

/** One status-change (or delete) action the list screen can offer for an experiment. */
internal enum class ExperimentAction(val targetStatus: String?) {
    Start("active"),
    Pause("paused"),
    Resume("active"),
    End("ended"),
    Delete(null),
}

/**
 * Which actions apply to an experiment in [status] — mirrors the server's transition table
 * (ExperimentStatus.kt): draft -> Start/Delete, active -> Pause/End, paused -> Resume/End,
 * ended -> none. Always empty when [canWrite] is false (reviewer role).
 */
internal fun availableActions(status: String, canWrite: Boolean): List<ExperimentAction> {
    if (!canWrite) return emptyList()
    return when (status) {
        "draft" -> listOf(ExperimentAction.Start, ExperimentAction.Delete)
        "active" -> listOf(ExperimentAction.Pause, ExperimentAction.End)
        "paused" -> listOf(ExperimentAction.Resume, ExperimentAction.End)
        else -> emptyList()
    }
}

/** `(count, limit)` for the "N of limit used" usage line, or null when [limit] is null
 *  (unlimited — paid plan or self-host), in which case the line isn't shown at all. */
internal fun usageLabelArgs(count: Int, limit: Int?): Pair<Int, Int>? =
    limit?.let { count to it }

/** The Experiments tab should auto-open its setup guide on entry exactly when it has never
 *  been [seen] (dismissed) before. */
internal fun shouldAutoOpenGuide(seen: Boolean): Boolean = !seen

/** Number of steps in the Experiments setup guide (see ExperimentsGuide.kt). */
internal const val EXPERIMENTS_GUIDE_STEP_COUNT = 5

/** Clamps forward paging at the last step (no wraparound). */
internal fun guideStepAfter(step: Int, totalSteps: Int): Int = (step + 1).coerceAtMost(totalSteps - 1)

/** Clamps backward paging at the first step (no wraparound). */
internal fun guideStepBefore(step: Int): Int = (step - 1).coerceAtLeast(0)

internal fun isLastGuideStep(step: Int, totalSteps: Int): Boolean = step == totalSteps - 1

// ---- Results verdict (Task 12) ----

/** The plain-language read of an experiment's results, for the results-panel banner. */
internal sealed class ExperimentVerdict {
    /** [ExperimentComparisonDto] was null — a variant is below the k-anonymity floor, or
     *  hasn't reached the minimum sample yet. Never render a fake 0%/tie in this case. */
    data object NotEnoughData : ExperimentVerdict()
    data class NoDifference(val pValue: Double) : ExperimentVerdict()
    data class Winner(val variantName: String, val pValue: Double) : ExperimentVerdict()
}

internal fun verdictFor(comparison: ExperimentComparisonDto?): ExperimentVerdict {
    if (comparison == null) return ExperimentVerdict.NotEnoughData
    return if (comparison.significant) {
        ExperimentVerdict.Winner(comparison.winner ?: comparison.challenger, comparison.pValue)
    } else {
        ExperimentVerdict.NoDifference(comparison.pValue)
    }
}

/** `0.0355` -> `"0.036"`; anything below 0.001 -> `"< 0.001"` (never shown as a misleading "0.000"). */
internal fun formatPValue(p: Double): String {
    if (p < 0.001) return "< 0.001"
    val thousandths = round(p * 1000).toInt()
    val whole = thousandths / 1000
    val frac = (thousandths % 1000).toString().padStart(3, '0')
    return "$whole.$frac"
}

// ---- Confidence meter (Task 29) ----

/**
 * Converts the server's two-sided p-value into the 0-100 confidence score shown on the
 * confidence meter. `confidence = (1 - pValue) * 100`, so the meter's 95% marker line lines up
 * exactly with the server's `significant = pValue < SIGNIFICANCE_LEVEL (0.05)` cutoff
 * (ExperimentStats.kt) — 95% confidence is the boundary, not an arbitrary UI choice. Rounded to
 * the nearest whole percent and clamped to `0..100` as a defensive guard against floating-point
 * drift at the extremes.
 */
internal fun confidenceScoreFor(pValue: Double): Int =
    round((1.0 - pValue) * 100).toInt().coerceIn(0, 100)

/**
 * The confidence score for an experiment's current [ExperimentVerdict], or `null` when there
 * isn't a comparison to score yet (see [ExperimentVerdict.NotEnoughData]) — never a fake 0%.
 */
internal fun confidenceScoreFor(verdict: ExperimentVerdict): Int? = when (verdict) {
    is ExperimentVerdict.NotEnoughData -> null
    is ExperimentVerdict.NoDifference -> confidenceScoreFor(verdict.pValue)
    is ExperimentVerdict.Winner -> confidenceScoreFor(verdict.pValue)
}

/**
 * The plain-language verdict sentence shown beside the confidence meter (README's Experiments
 * spec: "a confidence meter ... plain-language verdict sentence, raw stats in Expert mode").
 * The raw [ExperimentComparisonDto] stats (z, p-value) are formatted separately via
 * [formatPValue] for Expert mode — this sentence is the plain-language layer only.
 */
internal fun verdictSentence(verdict: ExperimentVerdict): String = when (verdict) {
    is ExperimentVerdict.NotEnoughData ->
        "Not enough data yet to call a winner."
    is ExperimentVerdict.NoDifference ->
        "No clear difference yet (${confidenceScoreFor(verdict.pValue)}% confidence) — keep collecting data."
    is ExperimentVerdict.Winner ->
        "${verdict.variantName} is winning with ${confidenceScoreFor(verdict.pValue)}% confidence."
}
