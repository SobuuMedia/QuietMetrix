package com.quietmetrix.server.funnels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FunnelResponse(
    @SerialName("funnel_key") val funnelKey: String,
    val name: String,
    val description: String? = null,
    val steps: List<FunnelStepDefinition>,
    @SerialName("window_seconds") val windowSeconds: Long,
    val source: String,
    val locked: Boolean,
    @SerialName("count_mode") val countMode: String = "actor",
    @SerialName("identity_scope") val identityScope: String = "install_or_session",
    @SerialName("correlation_property") val correlationProperty: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class FunnelsResponse(
    val funnels: List<FunnelResponse> = emptyList(),
)

@Serializable
data class CreateFunnelRequest(
    @SerialName("funnel_key") val funnelKey: String,
    val name: String,
    val description: String? = null,
    val steps: List<FunnelStepDefinition> = emptyList(),
    @SerialName("window_seconds") val windowSeconds: Long = FunnelValidation.DEFAULT_WINDOW_SECONDS,
    @SerialName("count_mode") val countMode: String = "actor",
    @SerialName("identity_scope") val identityScope: String = "install_or_session",
    @SerialName("correlation_property") val correlationProperty: String? = null,
)

@Serializable
data class UpdateFunnelRequest(
    val name: String? = null,
    val description: String? = null,
    val steps: List<FunnelStepDefinition>? = null,
    @SerialName("window_seconds") val windowSeconds: Long? = null,
)

@Serializable
data class RegisterFunnelsRequest(
    val funnels: List<CreateFunnelRequest> = emptyList(),
    val namespace: String? = null,
    val revision: Long? = null,
)

@Serializable
data class RegisterFunnelsResponse(
    val registered: List<String> = emptyList(),
    val skippedLocked: List<String> = emptyList(),
    val rejected: Map<String, String> = emptyMap(),
    val ignoredStaleManifest: Boolean = false,
)

@Serializable
data class FunnelSummaryDto(
    @SerialName("funnel_key") val funnelKey: String,
    val name: String,
    @SerialName("window_seconds") val windowSeconds: Long,
    val steps: List<FunnelStepDefinition>,
    @SerialName("count_mode") val countMode: String = "actor",
    @SerialName("identity_scope") val identityScope: String = "install_or_session",
    @SerialName("correlation_property") val correlationProperty: String? = null,
)

@Serializable
data class FunnelRangeDto(val from: String, val to: String)

@Serializable
data class FunnelStepResultDto(
    val key: String,
    val name: String? = null,
    val count: Int,
    @SerialName("conversion_from_entry") val conversionFromEntry: Double,
    @SerialName("conversion_from_previous") val conversionFromPrevious: Double,
    val dropped: Int,
    @SerialName("drop_rate") val dropRate: Double,
    @SerialName("median_ms_from_previous") val medianMsFromPrevious: Long? = null,
    @SerialName("p90_ms_from_previous") val p90MsFromPrevious: Long? = null,
)

@Serializable
data class FunnelBreakdownValueDto(
    val value: String,
    val entered: Int,
    @SerialName("overall_conversion") val overallConversion: Double,
    val steps: List<FunnelStepResultDto>,
)

@Serializable
data class FunnelBreakdownDto(
    val dimension: String,
    val values: List<FunnelBreakdownValueDto>,
)

@Serializable
data class FunnelTrendPointDto(
    val bucket: String,
    val entered: Int,
    val converted: Int,
    val conversion: Double,
)

@Serializable
data class FunnelResultsResponse(
    val funnel: FunnelSummaryDto,
    val range: FunnelRangeDto,
    @SerialName("counted_by") val countedBy: String,
    val entered: Int,
    val converted: Int,
    @SerialName("overall_conversion") val overallConversion: Double,
    @SerialName("median_total_ms") val medianTotalMs: Long? = null,
    val steps: List<FunnelStepResultDto>,
    val breakdown: FunnelBreakdownDto? = null,
    val trend: List<FunnelTrendPointDto>? = null,
    val truncated: Boolean,
)
