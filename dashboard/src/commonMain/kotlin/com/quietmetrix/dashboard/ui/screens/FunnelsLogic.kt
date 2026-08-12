package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.FunnelDto

/** Picks the funnel matching [selectedKey], or the first funnel if none selected/found. */
internal fun currentFunnel(funnels: List<FunnelDto>, selectedKey: String?): FunnelDto? =
    funnels.firstOrNull { it.funnelKey == selectedKey } ?: funnels.firstOrNull()

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
