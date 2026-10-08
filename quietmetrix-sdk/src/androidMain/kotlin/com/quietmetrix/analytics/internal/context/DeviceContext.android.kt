package com.quietmetrix.analytics.internal.context

actual class DeviceContext actual constructor() {
    actual val platform: String = "android"
    actual val deviceClass: String = runCatching {
        val widthDp = android.content.res.Resources.getSystem().configuration.smallestScreenWidthDp
        when { widthDp <= 0 -> "unknown"; widthDp >= 600 -> "tablet"; else -> "phone" }
    }.getOrDefault("unknown")
    actual val language: String? = java.util.Locale.getDefault()?.language
    actual val appVersion: String? = null
    actual val screenWidth: Int? = null
    actual val screenHeight: Int? = null
    actual val userAgent: String? = null
    actual val osName: String? = "Android"
    actual val osVersion: String? = android.os.Build.VERSION.RELEASE
    actual val browserName: String? = null
    actual val browserVersion: String? = null
    actual val deviceModel: String? = android.os.Build.MODEL
    actual val anonymousId: String = generateAnonymousId()

    /** ISO-3166 alpha-2 region from the device locale, or null when unset. */
    val country: String? = java.util.Locale.getDefault()?.country?.takeIf { it.isNotBlank() }
}
