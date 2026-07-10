package com.quietmetrix.analytics.internal.context

import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

actual class DeviceContext actual constructor() {
    actual val platform: String = "macos"
    actual val language: String? = NSProcessInfo.processInfo.hostName
    actual val appVersion: String? = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
    actual val screenWidth: Int? = null
    actual val screenHeight: Int? = null
    actual val userAgent: String? = null
    actual val osName: String? = "macOS"
    actual val osVersion: String? = NSProcessInfo.processInfo.operatingSystemVersionString
    actual val browserName: String? = null
    actual val browserVersion: String? = null
    actual val deviceModel: String? = null
    actual val anonymousId: String = generateAnonymousId()
}