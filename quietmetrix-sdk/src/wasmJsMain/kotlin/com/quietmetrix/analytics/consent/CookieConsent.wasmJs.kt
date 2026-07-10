package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.StorageKeys

actual fun hasCookieConsent(): Boolean {
    val key = StorageKeys.cookieConsent(ConfigHolder.config.storageKeyPrefix)
    return localStorageHasKey(key)
}

actual fun setCookieConsent(accepted: Boolean) {
    val key = StorageKeys.cookieConsent(ConfigHolder.config.storageKeyPrefix)
    localStorageSet(key, if (accepted) "1" else "0")
}

actual fun isTrackingAllowed(): Boolean {
    val cfg = ConfigHolder.configOrNull ?: return true  // pre-init: permissive, matches prior wasmJs behavior
    val key = StorageKeys.cookieConsent(cfg.storageKeyPrefix)
    val stored = localStorageGet(key)
    return when (stored) {
        "0" -> false
        "1" -> true
        else -> cfg.trackingAllowedByDefault
    }
}

private fun localStorageHasKey(key: String): Boolean =
    js("localStorage.getItem(key) !== null")

private fun localStorageGet(key: String): String? =
    js("localStorage.getItem(key)")

private fun localStorageSet(key: String, value: String): Unit =
    js("localStorage.setItem(key, value)")
