package com.quietmetrix.analytics.internal.context

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
private fun envValue(name: String): String? = getenv(name)?.toKString()?.ifEmpty { null }

actual class DeviceContext actual constructor() {
    actual val platform: String = "windows"
    actual val language: String? = envValue("LANG")?.substringBefore('.')?.substringBefore('_')
    actual val appVersion: String? = null
    actual val screenWidth: Int? = null
    actual val screenHeight: Int? = null
    actual val userAgent: String? = null
    actual val osName: String? = "Windows"
    actual val osVersion: String? = envValue("OS")
    actual val browserName: String? = null
    actual val browserVersion: String? = null
    actual val deviceModel: String? = null
    actual val anonymousId: String = generateAnonymousId()
}
