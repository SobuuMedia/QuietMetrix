package com.quietmetrix.analytics.internal.context

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.posix.uname
import platform.posix.utsname

@OptIn(ExperimentalForeignApi::class)
private fun envLanguage(): String? =
    getenv("LANG")?.toKString()?.substringBefore('.')?.substringBefore('_')?.ifEmpty { null }

@OptIn(ExperimentalForeignApi::class)
private fun kernelRelease(): String? = memScoped {
    val info = alloc<utsname>()
    if (uname(info.ptr) == 0) info.release.toKString().ifEmpty { null } else null
}

actual class DeviceContext actual constructor() {
    actual val platform: String = "linux"
    actual val language: String? = envLanguage()
    actual val appVersion: String? = null
    actual val screenWidth: Int? = null
    actual val screenHeight: Int? = null
    actual val userAgent: String? = null
    actual val osName: String? = "Linux"
    actual val osVersion: String? = kernelRelease()
    actual val browserName: String? = null
    actual val browserVersion: String? = null
    actual val deviceModel: String? = null
    actual val anonymousId: String = generateAnonymousId()
}
