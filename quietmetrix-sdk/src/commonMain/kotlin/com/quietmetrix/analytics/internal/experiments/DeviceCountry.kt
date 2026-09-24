package com.quietmetrix.analytics.internal.experiments

/**
 * The device's locale region as ISO-3166 alpha-2 uppercase (e.g. "ES"), or null when it
 * cannot be determined. Read entirely on-device — never transmitted in any network payload
 * (see [com.quietmetrix.analytics.internal.counters.CounterFlusher], which never reads this).
 * Used only for local country-eligibility checks in [ExperimentClient]. This is the device's
 * language/locale region, not a physical location.
 */
internal expect fun deviceCountry(): String?

private val REGION_REGEX = Regex("^[A-Za-z]{2}$")

/** Uppercases [raw] if it's a 2-letter region code, otherwise returns null. */
internal fun normalizeRegion(raw: String?): String? {
    if (raw == null || !REGION_REGEX.matches(raw)) return null
    return raw.uppercase()
}
