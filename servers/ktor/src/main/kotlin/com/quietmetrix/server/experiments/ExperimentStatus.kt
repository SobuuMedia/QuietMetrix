package com.quietmetrix.server.experiments

/** The valid status values for an experiment, and the rules for moving between them. */
object ExperimentStatus {
    val ALL = setOf("draft", "active", "paused", "ended", "shipped", "closed")

    /** Allows legacy transitions and resolution of active, paused or finished tests; shipped/closed are terminal. */
    fun canTransition(from: String, to: String): Boolean = when (from) {
        "draft" -> to == "active" || to == "ended"
        "active" -> to in setOf("paused", "ended", "shipped", "closed")
        "paused" -> to in setOf("active", "ended", "shipped", "closed")
        "ended" -> to in setOf("shipped", "closed")
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
