package com.quietmetrix.dashboard.api

import com.quietmetrix.dashboard.api.TimeRange.Companion.Default
import com.quietmetrix.dashboard.resources.Res
import com.quietmetrix.dashboard.resources.range_1d
import com.quietmetrix.dashboard.resources.range_1h
import com.quietmetrix.dashboard.resources.range_30d
import com.quietmetrix.dashboard.resources.range_7d
import com.quietmetrix.dashboard.resources.range_90d
import org.jetbrains.compose.resources.StringResource

/**
 * A selectable analytics time window. [token] is the wire value sent to the
 * backend as `?range=…`; [seconds] is its duration (used by the backend to
 * compute the `since` cutoff and kept here so the value is testable).
 */
enum class TimeRange(
    val token: String,
    val seconds: Long,
    val labelRes: StringResource,
    /** Sub-day windows are bucketed by hour in the daily/sessions charts. */
    val hourly: Boolean,
) {
    Hour1("1h", 3_600, Res.string.range_1h, hourly = true),
    Day1("1d", 86_400, Res.string.range_1d, hourly = true),
    Day7("7d", 604_800, Res.string.range_7d, hourly = false),
    Day30("30d", 2_592_000, Res.string.range_30d, hourly = false),
    Day90("90d", 7_776_000, Res.string.range_90d, hourly = false);

    companion object {
        /** Default window for the Overview screen. */
        val Default: TimeRange = Day7

        /** Parse a wire [token] back to a [TimeRange], falling back to [Default]. */
        fun fromToken(token: String?): TimeRange =
            entries.firstOrNull { it.token == token } ?: Default
    }
}
