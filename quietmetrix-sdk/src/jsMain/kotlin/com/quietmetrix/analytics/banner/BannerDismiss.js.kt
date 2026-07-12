package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.SafeLocalStorage
import com.quietmetrix.analytics.internal.StorageKeys

actual fun isBannerDismissedThisMonth(): Boolean {
    val key = StorageKeys.bannerDismissed(ConfigHolder.config.storageKeyPrefix)
    return SafeLocalStorage.get(key) == currentMonthToken()
}

actual fun markBannerDismissed() {
    val key = StorageKeys.bannerDismissed(ConfigHolder.config.storageKeyPrefix)
    SafeLocalStorage.set(key, currentMonthToken())
}

// "YYYY-M" using 0-based month, matching the prior sobuu-feed implementation which used JS
// `new Date().getMonth()` (0..11). Keeps localStorage values byte-identical with the wasmJs target.
private fun currentMonthToken(): String =
    js("(function(){var d=new Date();return d.getFullYear()+'-'+d.getMonth();})()")
