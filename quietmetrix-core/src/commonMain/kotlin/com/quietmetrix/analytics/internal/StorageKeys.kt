package com.quietmetrix.analytics.internal

internal object StorageKeys {
    fun cookieConsent(prefix: String): String = "${prefix}cookie_consent"
    fun bannerDismissed(prefix: String): String = "${prefix}banner_dismissed"
}
