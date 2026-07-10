package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.StorageKeys

actual fun isBannerDismissedThisMonth(): Boolean {
    val key = StorageKeys.bannerDismissed(ConfigHolder.config.storageKeyPrefix)
    return readBannerMatchesCurrentMonth(key)
}

actual fun markBannerDismissed() {
    val key = StorageKeys.bannerDismissed(ConfigHolder.config.storageKeyPrefix)
    writeCurrentMonth(key)
}

// Stored format is "YYYY-M" using 0-based month, matching the prior sobuu-feed implementation
// which used JS `new Date().getMonth()` (0..11). This ensures byte-identical localStorage values
// across the extraction.
private fun readBannerMatchesCurrentMonth(key: String): Boolean =
    js("(function(k){var s=localStorage.getItem(k);if(!s)return false;var d=new Date();return s===(d.getFullYear()+'-'+d.getMonth());})(key)")

private fun writeCurrentMonth(key: String): Unit =
    js("(function(k){var d=new Date();localStorage.setItem(k,d.getFullYear()+'-'+d.getMonth());})(key)")
