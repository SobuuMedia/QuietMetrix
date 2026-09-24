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
    val variants: List<VariantSpec>? = null,
    val countries: List<String>? = null,
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

fun ExperimentRecord.toConfigDto() = ExperimentConfigDto(
    key = experimentKey,
    trafficPercent = trafficPercent,
    variants = variants,
    countries = countries,
)
