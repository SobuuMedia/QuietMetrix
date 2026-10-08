package com.quietmetrix.server.experiments

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1/projects/{id}/experiments/{key}/results` response. */
@Serializable
data class ExperimentResultsResponse(
    val experiment: ExperimentDto,
    val range: ExperimentRangeDto,
    val unit: String = "sessions",
    val basis: String = "selected_range",
    val variants: List<ExperimentVariantResultDto>,
    val comparison: ExperimentComparisonDto? = null,
)

@Serializable
data class ExperimentRangeDto(val from: String, val to: String)

@Serializable
data class ExperimentVariantResultDto(
    val name: String,
    val impressions: Long? = null,
    val interactions: Long? = null,
    val rate: Double? = null,
)

@Serializable
data class ExperimentComparisonDto(
    val baseline: String,
    val challenger: String,
    val z: Double,
    @SerialName("p_value") val pValue: Double,
    val significant: Boolean,
    val winner: String? = null,
)

fun ExperimentVariantResult.toDto() = ExperimentVariantResultDto(name, impressions, interactions, rate)

fun ExperimentComparison.toDto() = ExperimentComparisonDto(baseline, challenger, z, pValue, significant, winner)
