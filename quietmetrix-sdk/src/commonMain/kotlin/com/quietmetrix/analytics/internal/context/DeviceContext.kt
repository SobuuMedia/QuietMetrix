package com.quietmetrix.analytics.internal.context

expect class DeviceContext() {
    val platform: String
    val language: String?
    val appVersion: String?
    val screenWidth: Int?
    val screenHeight: Int?
    val userAgent: String?
    val osName: String?
    val osVersion: String?
    val browserName: String?
    val browserVersion: String?
    val deviceModel: String?
    val anonymousId: String
}