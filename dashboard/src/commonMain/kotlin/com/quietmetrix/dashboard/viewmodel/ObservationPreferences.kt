package com.quietmetrix.dashboard.viewmodel

import kotlinx.serialization.Serializable

@Serializable
internal data class ObservationPreference(val project: String, val id: String, val hiddenOn: String, val snoozed: Boolean)

/** Snoozes expire on the next UTC date; permanent dismissals remain project-scoped. */
internal fun hiddenObservationIds(rows: List<ObservationPreference>, project: String?, today: String): Set<String> =
    rows.filter { it.project == project && (!it.snoozed || it.hiddenOn == today) }.map { it.id }.toSet()
