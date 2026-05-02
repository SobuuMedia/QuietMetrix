package com.quietmetrix.analytics.internal.context

actual class DeviceContext actual constructor() {
    actual val platform: String = "windows"
    actual val language: String? = System.getProperty("user.language")
    actual val appVersion: String? = null
    actual val screenWidth: Int? = null
    actual val screenHeight: Int? = null
    actual val userAgent: String? = null
    actual val osName: String? = "Windows"
    actual val osVersion: String? = System.getProperty("os.version")
    actual val browserName: String? = null
    actual val browserVersion: String? = null
    actual val deviceModel: String? = null
    actual val anonymousId: String = generateAnonymousId()
}