package com.quietmetrix.server.experiments

/** The valid status values for an experiment, and the rules for moving between them. */
object ExperimentStatus {
    val ALL = setOf("draft", "active", "paused", "ended")

    /**
     * Whether [from] -> [to] is an allowed transition. `draft -> active|ended`,
     * `active -> paused|ended`, `paused -> active|ended`; everything else (including a status
     * "changing" to its own current value, and anything from `ended`) is false.
     */
    fun canTransition(from: String, to: String): Boolean = when (from) {
        "draft" -> to == "active" || to == "ended"
        "active" -> to == "paused" || to == "ended"
        "paused" -> to == "active" || to == "ended"
        else -> false
    }

    /**
     * Which non-status fields may be edited while an experiment is in [status]. `variants` and
     * `countries` can only change before an experiment starts (`draft`); `name`/`screen`/
     * `traffic_percent` can change any time up to `ended`; nothing changes once `ended`.
     */
    fun editableFields(status: String): Set<String> = when (status) {
        "draft" -> setOf("name", "screen", "traffic_percent", "variants", "countries")
        "active", "paused" -> setOf("name", "screen", "traffic_percent")
        else -> emptySet()
    }
}
