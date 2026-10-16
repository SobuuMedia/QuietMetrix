package com.quietmetrix.dashboard.ui.screens

import com.quietmetrix.dashboard.api.FunnelDto
import com.quietmetrix.dashboard.api.FunnelResultsResponse
import kotlinx.serialization.json.Json

internal fun parseFunnelProperties(text: String): Map<String, String>? =
    runCatching { Json.decodeFromString<Map<String, String>>(text) }.getOrNull()?.takeIf {
        it.size <= 20 && it.all { (k,v) -> k.matches(Regex("^[A-Za-z0-9_.-]{1,64}$")) && v.length <= 128 }
    }
internal fun nextFunnelStepKey(keys: Set<String>): String = generateSequence(1) { it + 1 }.map { "step_$it" }.first { it !in keys }
internal fun funnelForResults(current: FunnelDto, results: FunnelResultsResponse?): FunnelDto = results?.funnel?.let {
    current.copy(steps=it.steps, revision=it.revision, windowSeconds=it.windowSeconds, countMode=it.countMode, identityScope=it.identityScope, correlationProperty=it.correlationProperty)
} ?: current
