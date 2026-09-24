package com.quietmetrix.analytics.internal.experiments

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One variant's display name and its share (1-99) of enrolled devices. */
@Serializable
internal data class VariantWeightDto(val name: String, val weight: Int)

/** The wire shape of `GET /api/v1/experiments/config`'s `experiments[]` entries — see
 *  servers/ktor/.../experiments/ExperimentModels.kt's ExperimentConfigDto, which this mirrors. */
@Serializable
internal data class ExperimentConfigItemDto(
    val key: String,
    @SerialName("traffic_percent") val trafficPercent: Int,
    val variants: List<VariantWeightDto>,
    val countries: List<String>? = null,
)

@Serializable
internal data class ExperimentConfigResponseDto(val experiments: List<ExperimentConfigItemDto> = emptyList())

/** An active experiment's definition, exactly as needed to assign a variant on-device. */
internal data class ExperimentSpec(
    val key: String,
    val trafficPercent: Int,
    val variants: List<VariantWeightDto>,
    val countries: List<String>?,
)

private val configJson = Json { ignoreUnknownKeys = true }

/**
 * Parses a `GET /api/v1/experiments/config` response body into a `key -> spec` map. Malformed
 * JSON, or any entry that doesn't have exactly 2 variants, is dropped rather than thrown —
 * a config fetch must never crash the host app; an unparseable experiment is simply
 * unavailable (getVariant returns "none" for it), same as if it had never been fetched.
 */
internal fun parseExperimentConfig(json: String): Map<String, ExperimentSpec> {
    val parsed = try {
        configJson.decodeFromString(ExperimentConfigResponseDto.serializer(), json)
    } catch (_: Exception) {
        return emptyMap()
    }
    return parsed.experiments
        .filter { it.variants.size == 2 }
        .associate { it.key to ExperimentSpec(it.key, it.trafficPercent, it.variants, it.countries) }
}

/** Whether [json] is well-formed enough to persist as the cached config — an empty
 *  `{"experiments":[]}` (no active experiments right now) is valid and should still be cached. */
internal fun isValidExperimentConfigJson(json: String): Boolean =
    try {
        configJson.decodeFromString(ExperimentConfigResponseDto.serializer(), json)
        true
    } catch (_: Exception) {
        false
    }
