package com.quietmetrix.analytics.internal.context

actual class DeviceContext actual constructor() {
    actual val platform: String = "jvm"
    actual val language: String? = System.getProperty("user.language")
    actual val appVersion: String? = null
    actual val screenWidth: Int? = null
    actual val screenHeight: Int? = null
    actual val userAgent: String? = System.getProperty("os.name")
    actual val osName: String? = System.getProperty("os.name")
    actual val osVersion: String? = System.getProperty("os.version")
    actual val browserName: String? = null
    actual val browserVersion: String? = null
    actual val deviceModel: String? = null
    actual val anonymousId: String = generateAnonymousId()
}