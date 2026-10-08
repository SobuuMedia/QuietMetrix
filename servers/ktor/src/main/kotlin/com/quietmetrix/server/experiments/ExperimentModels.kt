package com.quietmetrix.server.experiments

import com.quietmetrix.server.persistence.ExperimentRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One variant of an experiment: a display name and its share (1-99) of enrolled devices. */
@Serializable
data class VariantSpec(val name: String, val weight: Int)

/** Input to [com.quietmetrix.server.persistence.ExperimentRepository.create]/`update`. */
data class ExperimentDefinition(
    val experimentKey: String,
    val name: String,
    val screen: String?,
    val trafficPercent: Int,
    val variants: List<VariantSpec>,
    val countries: List<String>?,
    val mode: String = "legacy",
    val placementKey: String? = null,
    val revision: Int = 0,
    val bPercent: Int? = null,
    val languages: List<String>? = null,
    val matchMode: String = "all",
    val goalEvent: String? = null,
    val iconKey: String? = null,
)

/** Wire shape of one experiment in dashboard-facing responses. */
@Serializable
data class ExperimentDto(
    val key: String,
    val name: String,
    val screen: String? = null,
    val status: String,
    @SerialName("traffic_percent") val trafficPercent: Int,
    val variants: List<VariantSpec>,
    val countries: List<String>? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("selected_variant") val selectedVariant: String? = null,
    val mode: String = "legacy",
    @SerialName("placement_key") val placementKey: String? = null,
    val revision: Int = 0,
    @SerialName("b_percent") val bPercent: Int? = null,
    val languages: List<String>? = null,
    @SerialName("match_mode") val matchMode: String = "all",
    @SerialName("goal_event") val goalEvent: String? = null,
    @SerialName("icon_key") val iconKey: String? = null,
)

fun ExperimentRecord.toDto() = ExperimentDto(
    key = experimentKey,
    name = name,
    screen = screen,
    status = status,
    trafficPercent = trafficPercent,
    variants = variants,
    countries = countries,
    createdAt = createdAt,
    startedAt = startedAt,
    endedAt = endedAt,
    selectedVariant = selectedVariant,
    mode = mode,
    placementKey = placementKey,
    revision = revision,
    bPercent = bPercent,
    languages = languages,
    matchMode = matchMode,
    goalEvent = goalEvent,
    iconKey = iconKey,
)

/** `GET /api/v1/projects/{id}/experiments` response. `limit` is null when unlimited. */
@Serializable
data class ExperimentsResponse(val experiments: List<ExperimentDto>, val limit: Int? = null)

/** `POST /api/v1/projects/{id}/experiments` body. Omitted fields fall back to plan defaults. */
@Serializable
data class CreateExperimentRequest(
    val key: String,
    val name: String,
    val screen: String? = null,
    @SerialName("traffic_percent") val trafficPercent: Int? = null,
    val variants: List<VariantSpec>? = null,
    val countries: List<String>? = null,
    val mode: String? = null,
    @SerialName("placement_key") val placementKey: String? = null,
    @SerialName("b_percent") val bPercent: Int? = null,
    val languages: List<String>? = null,
    @SerialName("match_mode") val matchMode: String? = null,
    @SerialName("goal_event") val goalEvent: String? = null,
    @SerialName("icon_key") val iconKey: String? = null,
)

/** Default variant pair used when [CreateExperimentRequest.variants] is omitted. */
val DEFAULT_VARIANTS = listOf(VariantSpec("a", 50), VariantSpec("b", 50))

/** `PATCH /api/v1/projects/{id}/experiments/{key}` body — every field optional. */
@Serializable
data class UpdateExperimentRequest(
    val name: String? = null,
    val screen: String? = null,
    @SerialName("traffic_percent") val trafficPercent: Int? = null,
    val status: String? = null,
    @SerialName("selected_variant") val selectedVariant: String? = null,
    val variants: List<VariantSpec>? = null,
    val countries: List<String>? = null,
    val mode: String? = null,
    @SerialName("placement_key") val placementKey: String? = null,
    @SerialName("b_percent") val bPercent: Int? = null,
    val languages: List<String>? = null,
    @SerialName("match_mode") val matchMode: String? = null,
    @SerialName("goal_event") val goalEvent: String? = null,
    @SerialName("icon_key") val iconKey: String? = null,
)

/** `GET /api/v1/experiments/config` response — the minimal, SDK-facing config shape. */
@Serializable
data class ExperimentConfigResponse(val experiments: List<ExperimentConfigDto>)

@Serializable
data class ExperimentConfigDto(
    val key: String,
    @SerialName("traffic_percent") val trafficPercent: Int,
    val variants: List<VariantSpec>,
    val countries: List<String>? = null,
)

/** Projects a shipped choice to full rollout without mutating the original allocation. */
fun ExperimentRecord.toConfigDto() = ExperimentConfigDto(
    key = experimentKey,
    trafficPercent = if (status == "shipped") 100 else trafficPercent,
    variants = if (status == "shipped") variants.map { it.copy(weight = if (it.name == selectedVariant) 100 else 0) } else variants,
    countries = if (status == "shipped") null else countries,
)

/** Versioned SDK payload for visibility/targeting experiments. The legacy v1 DTO remains intact. */
@Serializable
data class ExperimentConfigV2Response(val schema: Int = 2, val experiments: List<ExperimentConfigV2Dto>)

@Serializable
data class ExperimentConfigV2Dto(
    val key: String,
    val revision: Int,
    val mode: String,
    val status: String,
    @SerialName("placement_key") val placementKey: String? = null,
    @SerialName("b_percent") val bPercent: Int,
    val variants: List<ExperimentVariantV2Dto>,
    val audience: ExperimentAudienceDto,
    @SerialName("goal_event") val goalEvent: String? = null,
    @SerialName("selected_variant") val selectedVariant: String? = null,
)

@Serializable
data class ExperimentVariantV2Dto(val id: String, val label: String, val weight: Int)

@Serializable
data class ExperimentAudienceDto(
    val countries: List<String>? = null,
    val languages: List<String>? = null,
    @SerialName("match_mode") val matchMode: String = "all",
)

fun ExperimentRecord.toConfigV2Dto() = ExperimentConfigV2Dto(
    key = experimentKey,
    revision = revision,
    mode = mode,
    status = status,
    placementKey = placementKey,
    bPercent = if (status == "shipped") if (selectedVariant?.let { name -> variants.indexOfFirst { it.name == name } } == 1) 100 else 0 else (bPercent ?: 50),
    variants = variants.mapIndexed { i, v ->
        val id = if (i == 0) "a" else "b"
        ExperimentVariantV2Dto(id, v.name, if (status == "shipped") if (id == selectedVariant) 100 else 0 else v.weight)
    },
    audience = ExperimentAudienceDto(countries, languages, matchMode),
    goalEvent = goalEvent,
    selectedVariant = selectedVariant?.let { name -> variants.indexOfFirst { it.name == name }.takeIf { it >= 0 }?.let { if (it == 0) "a" else "b" } },
)
