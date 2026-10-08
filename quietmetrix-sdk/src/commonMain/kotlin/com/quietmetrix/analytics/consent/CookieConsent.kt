package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.PersistentPreferences
import com.quietmetrix.analytics.internal.counters.MetricGateway

/** Returns true if the user has already made a cookie consent choice. */
fun hasCookieConsent(): Boolean = runCatching { PersistentPreferences.get("cookie_consent") in setOf("0", "1") }.getOrDefault(false)

/** Persists the user's cookie consent choice and refreshes any live experiment placements. */
fun setCookieConsent(accepted: Boolean) {
    persistCookieConsent(accepted)
    if (accepted) QuietMetrix.onConsentGranted()
    else MetricGateway.purge()
    notifyExperimentDecisionObservers()
}

internal fun persistCookieConsent(accepted: Boolean) {
    PersistentPreferences.set("cookie_consent", if (accepted) "1" else "0")
}

/**
 * Returns true if analytics events should be sent.
 * Tracking is allowed when the user accepted, or — if [QuietMetrixConfig.trackingAllowedByDefault]
 * is true — when they have not yet made a choice. Tracking is blocked only when the user
 * explicitly declined.
 */
fun isTrackingAllowed(): Boolean {
    val config = ConfigHolder.configOrNull ?: return false
    return runCatching {
        when (PersistentPreferences.get("cookie_consent")) {
            "1" -> true
            "0" -> false
            null -> config.trackingAllowedByDefault
            else -> false
        }
    }.getOrDefault(false)
}
