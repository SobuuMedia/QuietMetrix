package com.quietmetrix.server.counters

import java.security.MessageDigest
import java.time.LocalDate

/** Strict registry for the additive hourly protocol. Keep its shapes and canonical hash in PHP. */
object CounterRegistryV2 {
    private val shapes = mapOf(
        "unique_window_v2" to setOf("scope_kind", "scope_key", "days", "end_day"),
        "unique_hour_v2" to setOf("scope_kind", "scope_key"),
        "witness_window_v2" to setOf("target_metric", "target_hash", "days", "end_day"),
        "visit_start_v2" to emptySet(), "visit_duration_ms_v2" to emptySet(),
        "visit_complete_v2" to emptySet(), "screen_view_v2" to setOf("screen"),
        "screen_dwell_ms_v2" to setOf("screen"), "screen_dwell_sample_v2" to setOf("screen"),
        "event_v2" to setOf("name"), "crash_v2" to emptySet(), "error_v2" to emptySet(),
        "screen_transition_v2" to setOf("from", "to"), "search_v2" to setOf("screen"),
        "search_zero_result_v2" to setOf("screen"), "retention_entry_v2" to setOf("entry_day"),
        "retention_return_v2" to setOf("entry_day", "age_day"),
        "funnel_step_v2" to setOf("namespace", "f", "revision", "step", "entry_day"),
        "experiment_exposure_v2" to setOf("exp", "revision", "variant"),
        "experiment_goal_v2" to setOf("exp", "revision", "variant"),
        "sdk_presence_v2" to setOf("sdk_version", "app_version", "os_family", "os_major"),
    )
    private val keyPattern = Regex("^[a-z0-9_]{1,32}$")
    private val valuePattern = Regex("^[A-Za-z0-9_.:/-]{1,128}$")
    private const val unit = '\u001f'
    private const val record = '\u001e'

    data class Valid(val canonicalDims: String, val hash: String)
    sealed interface Result {
        data class Accepted(val value: Valid) : Result
        data class Rejected(val reason: String) : Result
    }

    fun validate(metric: String, dims: Map<String, String>): Result {
        val required = shapes[metric] ?: return Result.Rejected("unknown_metric")
        if (dims.keys != required) return Result.Rejected("dims_mismatch")
        if (dims.any { (key, value) -> !keyPattern.matches(key) || !valuePattern.matches(value) })
            return Result.Rejected("dim_value_invalid")
        fun day(key: String) = runCatching { LocalDate.parse(dims.getValue(key)).toString() }.getOrNull() == dims[key]
        when (metric) {
            "unique_window_v2" -> if (!validWindow(dims, "scope_kind", "scope_key")) return Result.Rejected("unique_window_invalid")
            "witness_window_v2" -> if (!validDays(dims) || !day("end_day") || !Regex("^[a-f0-9]{64}$").matches(dims.getValue("target_hash")) ||
                dims.getValue("target_metric") !in shapes.keys - setOf("unique_window_v2", "unique_hour_v2", "witness_window_v2")) return Result.Rejected("witness_window_invalid")
            "unique_hour_v2" -> if (dims["scope_kind"] !in setOf("project", "screen") ||
                (dims["scope_kind"] == "project" && dims["scope_key"] != "all") ||
                (dims["scope_kind"] == "screen" && dims["scope_key"] in setOf("all", "__exit__"))) return Result.Rejected("unique_scope_invalid")
            "screen_view_v2", "screen_dwell_ms_v2", "screen_dwell_sample_v2", "search_v2", "search_zero_result_v2" ->
                if (dims["screen"] == "__exit__") return Result.Rejected("reserved_screen")
            "screen_transition_v2" -> if (dims["from"] == "__exit__" || (dims["to"] == "__exit__" && dims["from"].isNullOrBlank())) return Result.Rejected("invalid_transition")
            "retention_entry_v2" -> if (!day("entry_day")) return Result.Rejected("entry_day_invalid")
            "retention_return_v2" -> if (!day("entry_day") || dims["age_day"] !in setOf("1", "3", "7", "14", "30")) return Result.Rejected("retention_return_invalid")
            "funnel_step_v2" -> if (!day("entry_day") || dims["revision"]?.toIntOrNull()?.let { it > 0 } != true || dims["step"]?.toIntOrNull()?.let { it in 1..10 } != true) return Result.Rejected("funnel_step_invalid")
            "experiment_exposure_v2", "experiment_goal_v2" -> if (dims["revision"]?.toIntOrNull()?.let { it > 0 } != true || dims["variant"] !in setOf("a", "b")) return Result.Rejected("experiment_cell_invalid")
        }
        val canonical = dims.toSortedMap().entries.joinToString(record.toString()) { "${it.key}$unit${it.value}" }
        val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return Result.Accepted(Valid(canonical, hash))
    }

    /**
     * Hashes only the bounded, business-selected dimensions for the project-wide cardinality
     * budget. Cohort dates and rolling-window end dates describe when a cell applies; counting
     * them as distinct choices would exhaust the 500-cell cap as a project collects history.
     * Keep this projection byte-for-byte aligned with counterV2CardinalityHash in PHP.
     */
    fun cardinalityHash(metric: String, dims: Map<String, String>): String {
        val timeKeys = setOf("entry_day", "end_day") +
            if (metric == "witness_window_v2") setOf("target_hash") else emptySet()
        val bounded = dims.filterKeys { it !in timeKeys }.toSortedMap()
        val canonical = bounded.entries.joinToString(record.toString()) { "${it.key}$unit${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun validWindow(dims: Map<String, String>, kind: String, key: String): Boolean =
        validDays(dims) && runCatching { LocalDate.parse(dims.getValue("end_day")).toString() }.getOrNull() == dims["end_day"] &&
            dims[kind] in setOf("project", "screen") &&
            (dims[kind] == "project" && dims[key] == "all" || dims[kind] == "screen" && dims[key] !in setOf("all", "__exit__"))

    private fun validDays(dims: Map<String, String>): Boolean = dims["days"]?.toIntOrNull() in (1..7).toList() + listOf(30, 90)
}
