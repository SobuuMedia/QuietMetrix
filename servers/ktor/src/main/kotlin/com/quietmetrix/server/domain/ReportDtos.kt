package com.quietmetrix.server.domain

import kotlinx.serialization.Serializable

@Serializable
data class ReportMeasure(
    val value: Long? = null,
    val state: String,
    val unit: String,
    val source: String,
    val contributors: Long? = null,
    /** Optional 0..1 rate when value is a numerator and contributors is its denominator. */
    val rate: Double? = null,
)

@Serializable
data class ReportCoverage(
    val source: String = "v2",
    val resolution: String = "daily",
    val oldest_day: String? = null,
    val newest_day: String? = null,
    val sdk_versions: List<String> = emptyList(),
    val legacy_present: Boolean = false,
    val traffic_class_available: Boolean = false,
    val traffic_class_supported: Boolean = true,
)

@Serializable
data class ReportBin(
    val start_day: String,
    val end_day: String,
    val hour: Int? = null,
    val measures: Map<String, ReportMeasure>,
)

@Serializable
data class ReportRetentionCohort(
    val entry_day: String,
    val size: ReportMeasure,
    val days: Map<String, ReportMeasure>,
)

@Serializable
data class ProjectReportResponse(
    val scope: Map<String, String>,
    val generated_at: String,
    val last_received_at: String? = null,
    val coverage: ReportCoverage,
    val overview: Map<String, ReportMeasure>,
    val traffic: Map<String, ReportMeasure> = emptyMap(),
    val charts: Map<String, List<ReportBin>> = emptyMap(),
    val comparison_charts: Map<String, List<ReportBin>> = emptyMap(),
    val comparison: Map<String, ReportMeasure> = emptyMap(),
    val flow: Map<String, ReportMeasure> = emptyMap(),
    val retention: Map<String, ReportMeasure> = emptyMap(),
    val retention_cohorts: List<ReportRetentionCohort> = emptyList(),
    val comparison_retention_cohorts: List<ReportRetentionCohort> = emptyList(),
    val definitions: Map<String, List<String>> = emptyMap(),
)
