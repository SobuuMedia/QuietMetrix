package com.quietmetrix.analytics.internal.context

import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.UIKit.UIDevice

actual class DeviceContext actual constructor() {
    actual val platform: String = "ios"
    actual val language: String? = NSLocale.currentLocale.languageCode
    actual val appVersion: String? = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
    actual val screenWidth: Int? = null
    actual val screenHeight: Int? = null
    actual val userAgent: String? = null
    actual val osName: String? = "iOS"
    actual val osVersion: String? = UIDevice.currentDevice.systemVersion
    actual val browserName: String? = null
    actual val browserVersion: String? = null
    actual val deviceModel: String? = UIDevice.currentDevice.model
    actual val anonymousId: String = generateAnonymousId()
}