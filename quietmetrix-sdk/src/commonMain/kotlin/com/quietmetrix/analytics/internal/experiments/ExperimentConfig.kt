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

@Serializable
internal data class ExperimentV2VariantDto(val id: String, val label: String, val weight: Int)

@Serializable
internal data class ExperimentV2AudienceDto(
    val countries: List<String>? = null,
    val languages: List<String>? = null,
    @SerialName("match_mode") val matchMode: String = "all",
)

@Serializable
internal data class ExperimentV2ItemDto(
    val key: String,
    val revision: Int,
    val mode: String,
    val status: String,
    @SerialName("placement_key") val placementKey: String? = null,
    @SerialName("b_percent") val bPercent: Int,
    val variants: List<ExperimentV2VariantDto>,
    val audience: ExperimentV2AudienceDto = ExperimentV2AudienceDto(),
    @SerialName("goal_event") val goalEvent: String? = null,
    @SerialName("selected_variant") val selectedVariant: String? = null,
)

@Serializable
internal data class ExperimentV2ConfigResponseDto(
    val schema: Int = 2,
    val experiments: List<ExperimentV2ItemDto> = emptyList(),
)

/** Stable direct B percentage assignment; an existing seed never changes during process reorder. */
internal fun experimentV2VariantForSeed(spec: ExperimentV2ItemDto, localSeed: String): String =
    spec.selectedVariant ?: if (bucketOf("${spec.key}:${spec.revision}:$localSeed") < spec.bPercent) "b" else "a"

/** Country and language are independent optional app inputs, never inferred from one another. */
internal fun experimentAudienceMatches(
    audience: ExperimentV2AudienceDto,
    countryCode: String?,
    languageTag: String?,
): Boolean {
    val countries = audience.countries.orEmpty()
    val languages = audience.languages.orEmpty().map { it.lowercase().substringBefore('-').substringBefore('_') }
    val countryConfigured = countries.isNotEmpty()
    val languageConfigured = languages.isNotEmpty()
    val countryMatch = countryConfigured && countryCode?.uppercase() in countries
    val languageMatch = languageConfigured && languageTag?.lowercase()?.substringBefore('-')?.substringBefore('_') in languages
    return when {
        !countryConfigured && !languageConfigured -> true
        countryConfigured && languageConfigured && audience.matchMode == "all" -> countryMatch && languageMatch
        countryConfigured && languageConfigured -> countryMatch || languageMatch
        countryConfigured -> countryMatch
        else -> languageMatch
    }
}

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

internal fun parseExperimentV2Config(json: String): Map<String, ExperimentV2ItemDto> = try {
    configJson.decodeFromString(ExperimentV2ConfigResponseDto.serializer(), json)
        .experiments
        .filter { item ->
            item.revision > 0 && item.bPercent in 0..100 &&
                item.mode in setOf("visibility", "variants") &&
                item.status in setOf("active", "shipped") &&
                item.variants.map { it.id }.toSet() == setOf("a", "b") &&
                item.variants.sumOf { it.weight } == 100 &&
                item.audience.matchMode in setOf("all", "any")
        }
        .associateBy { it.key }
} catch (_: Exception) {
    emptyMap()
}

internal fun isValidExperimentV2ConfigJson(json: String): Boolean = try {
    configJson.decodeFromString(ExperimentV2ConfigResponseDto.serializer(), json)
    true
} catch (_: Exception) {
    false
}
