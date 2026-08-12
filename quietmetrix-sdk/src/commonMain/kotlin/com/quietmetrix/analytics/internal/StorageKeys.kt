package com.quietmetrix.analytics.internal

internal object StorageKeys {
    fun cookieConsent(prefix: String): String = "${prefix}cookie_consent"
    fun bannerDismissed(prefix: String): String = "${prefix}banner_dismissed"
    fun analyticsEnabled(prefix: String): String = "${prefix}analytics_enabled"
    fun sessionId(prefix: String): String = "${prefix}session_id"
    fun anonymousId(prefix: String): String = "${prefix}anonymous_id"
    fun sessionNumber(prefix: String): String = "${prefix}session_number"
}

internal fun generateSid(storageKeyPrefix: String): String {
    val key = StorageKeys.sessionId(storageKeyPrefix)
    val existing = InMemoryStore.get(key)
    if (existing != null) return existing
    val newSid = "qm_sid_" + kotlin.random.Random.nextBytes(16).joinToString("") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
    InMemoryStore.set(key, newSid)
    return newSid
}
