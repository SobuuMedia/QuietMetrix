package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.StorageKeys

actual fun hasCookieConsent(): Boolean {
    val key = StorageKeys.cookieConsent(ConfigHolder.config.storageKeyPrefix)
    return InMemoryStore.has(key)
}

actual fun setCookieConsent(accepted: Boolean) {
    val key = StorageKeys.cookieConsent(ConfigHolder.config.storageKeyPrefix)
    InMemoryStore.set(key, if (accepted) "1" else "0")
}

actual fun isTrackingAllowed(): Boolean {
    val cfg = ConfigHolder.configOrNull ?: return true
    val key = StorageKeys.cookieConsent(cfg.storageKeyPrefix)
    return when (InMemoryStore.get(key)) {
        "0" -> false
        "1" -> true
        else -> cfg.trackingAllowedByDefault
    }
}
