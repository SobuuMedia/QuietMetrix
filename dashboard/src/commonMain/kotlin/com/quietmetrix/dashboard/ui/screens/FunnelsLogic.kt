package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import kotlin.math.roundToInt

/** Picks the funnel matching [selectedKey], or the first funnel if none selected/found. */
internal fun currentFunnel(funnels: List<FunnelDto>, selectedKey: String?): FunnelDto? =
    funnels.firstOrNull { it.funnelKey == selectedKey } ?: funnels.firstOrNull()

/**
 * The real number (0-10) the Funnels H1 headline highlights — design handoff: "Out of 10 people
 * who open Shelves, 4 end up adding a book" (`design_handoff_dashboard_redesign/README.md` >
 * Funnels). Scales [FunnelResultsResponse.overallConversion] (itself `converted / entered`, both
 * genuinely tracked counts — see [FunnelResultsResponse]) onto a friendly "out of 10" framing,
 * the same honest-rounding idiom `RetentionLogic.kt`'s `dotCountForFraction` already uses for
 * Retention's headline/card dot counts — not a second, independently-invented rounding rule.
 *
 * Returns null when [results] hasn't loaded yet or has zero entrants: there is no honest "N out
 * of 10" to report from zero real attempts in this range (the caller falls back to
 * `Res.string.funnel_no_entrants` for that case, same string the steps section below it already
 * uses for the identical condition).
 */
internal fun funnelHeadlineOutOfTen(results: FunnelResultsResponse?): Int? {
    if (results == null || results.entered <= 0) return null
    return (results.overallConversion * 10).roundToInt().coerceIn(0, 10)
}

private val FUNNEL_KEY_PATTERN = Regex("^[a-z0-9_-]{1,64}$")

/** Mirrors the server's validateFunnelDefinition() key-slug rule, for instant editor feedback. */
internal fun isValidFunnelKey(key: String): Boolean = FUNNEL_KEY_PATTERN.matches(key)

/**
 * True when the funnel-editor form has enough to submit: a valid key, a non-blank name, and
 * at least two steps each with a non-blank event name. The server re-validates fully — this
 * only gates the submit button so an obviously-incomplete form can't even be sent.
 */
internal fun isFunnelFormValid(funnelKey: String, name: String, stepEvents: List<String>): Boolean =
    isValidFunnelKey(funnelKey) && name.isNotBlank() && stepEvents.size >= 2 && stepEvents.all { it.isNotBlank() }
