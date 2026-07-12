package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.SafeLocalStorage
import com.quietmetrix.analytics.internal.StorageKeys

actual fun hasCookieConsent(): Boolean {
    val key = StorageKeys.cookieConsent(ConfigHolder.config.storageKeyPrefix)
    return SafeLocalStorage.has(key)
}

actual fun setCookieConsent(accepted: Boolean) {
    val key = StorageKeys.cookieConsent(ConfigHolder.config.storageKeyPrefix)
    SafeLocalStorage.set(key, if (accepted) "1" else "0")
}

actual fun isTrackingAllowed(): Boolean {
    val cfg = ConfigHolder.configOrNull ?: return true  // pre-init: permissive, matches wasmJs behavior
    val key = StorageKeys.cookieConsent(cfg.storageKeyPrefix)
    return when (SafeLocalStorage.get(key)) {
        "0" -> false
        "1" -> true
        else -> cfg.trackingAllowedByDefault
    }
}
