package com.quietmetrix.analytics

/** Returns true if the user has already made a cookie consent choice. */
expect fun hasCookieConsent(): Boolean

/** Persists the user's cookie consent choice. */
expect fun setCookieConsent(accepted: Boolean)

/**
 * Returns true if analytics events should be sent.
 * Tracking is allowed when the user accepted, or — if [QuietMetrixConfig.trackingAllowedByDefault]
 * is true — when they have not yet made a choice. Tracking is blocked only when the user
 * explicitly declined.
 */
expect fun isTrackingAllowed(): Boolean
