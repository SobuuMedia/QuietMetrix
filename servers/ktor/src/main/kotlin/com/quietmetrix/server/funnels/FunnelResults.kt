package com.quietmetrix.server.funnels

/**
 * The result shape for `GET /funnels/{key}/results`, produced by [FunnelCounterAnalyzer].
 * `breakdown` and `trend` are always null under aggregate-only ingest (see
 * [FunnelCounterAnalyzer]'s doc comment for what's gone versus deferred) but stay nullable
 * fields here — not removed — so the wire response shape (and the dashboard reading it) needs
 * no change.
 */
data class FunnelStepResult(
    val key: String,
    val name: String?,
    val count: Int,
    val conversionFromEntry: Double,
    val conversionFromPrevious: Double,
    val dropped: Int,
    val dropRate: Double,
    val medianMsFromPrevious: Long?,
    val p90MsFromPrevious: Long?,
)

data class FunnelBreakdownValue(
    val value: String,
    val entered: Int,
    val overallConversion: Double,
    val steps: List<FunnelStepResult>,
)

data class FunnelBreakdown(
    val dimension: String,
    val values: List<FunnelBreakdownValue>,
)

data class FunnelTrendPoint(
    val bucket: String,
    val entered: Int,
    val converted: Int,
    val conversion: Double,
)

data class FunnelResults(
    val entered: Int,
    val converted: Int,
    val overallConversion: Double,
    val medianTotalMs: Long?,
    val steps: List<FunnelStepResult>,
    val breakdown: FunnelBreakdown?,
    val trend: List<FunnelTrendPoint>?,
    /** Always "install" (or "install attempt" under [com.quietmetrix.server.funnels.FunnelCounterAnalyzer]'s ATTEMPT
     *  mode) now that evaluation is always device-local — see [FunnelCounterAnalyzer]. */
    val countedBy: String,
)
