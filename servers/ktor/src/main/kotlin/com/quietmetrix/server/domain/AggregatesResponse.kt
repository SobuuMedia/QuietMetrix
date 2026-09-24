package com.quietmetrix.server.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/v1/projects/{id}/aggregates` (and `/_demo/aggregates`) response — mirrors
 * dashboard/.../api/Models.kt's `AggregatesResponse` field-for-field. A typed response is
 * required here: kotlinx.serialization cannot serialize a raw heterogeneous `Map<String, Any>`
 * (it threw `Serializing collections of different element types is not yet supported` when
 * this endpoint built its body that way), so every field must be a concrete, `@Serializable`
 * type.
 */
@Serializable
data class AggregatesResponse(
    val demo: Boolean = false,
    @SerialName("window_days") val windowDays: Int,
    val totals: AggregatesTotals,
    @SerialName("top_events") val topEvents: List<TopEventItem> = emptyList(),
    @SerialName("top_screens") val topScreens: List<TopScreenItem> = emptyList(),
    @SerialName("screen_durations") val screenDurations: List<ScreenDurationItem> = emptyList(),
    @SerialName("top_values") val topValues: List<TopValueItem> = emptyList(),
    val daily: List<DailyPointItem> = emptyList(),
    val countries: List<BreakdownItem> = emptyList(),
    val platforms: List<BreakdownItem> = emptyList(),
    @SerialName("device_classes") val deviceClasses: List<BreakdownItem> = emptyList(),
)

@Serializable
data class AggregatesTotals(val events: Long = 0, val offline: Long = 0, val errors: Long = 0)

@Serializable
data class TopEventItem(@SerialName("event_name") val eventName: String?, val count: Long)

@Serializable
data class TopScreenItem(val screen: String?, val count: Long)

@Serializable
data class ScreenDurationItem(
    val screen: String?,
    val count: Long,
    @SerialName("avg_ms") val avgMs: Long,
    @SerialName("total_ms") val totalMs: Long,
)

@Serializable
data class TopValueItem(val name: String?, @SerialName("total_minor_units") val totalMinorUnits: Long)

@Serializable
data class DailyPointItem(val day: String, val total: Long, @SerialName("offline_total") val offlineTotal: Long = 0)

@Serializable
data class BreakdownItem(val name: String?, val count: Long)
