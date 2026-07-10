package com.quietmetrix.analytics.internal.context

// Kotlin/Wasm requires js(...) calls to be the sole expression of a top-level
// function body or property initializer, so browser lookups are declared as
// @JsFun externals rather than inline js("...") as? T casts.

@JsFun("() => navigator.language ? navigator.language.split('-')[0] : null")
private external fun jsLanguage(): String?

@JsFun("() => window.innerWidth")
private external fun jsScreenWidth(): Int

@JsFun("() => window.innerHeight")
private external fun jsScreenHeight(): Int

@JsFun("() => navigator.userAgent || null")
private external fun jsUserAgent(): String?

@JsFun(
    "() => { var ua = navigator.userAgent; " +
        "if (/Windows/.test(ua)) return 'Windows'; " +
        "if (/Mac OS X/.test(ua)) return 'macOS'; " +
        "if (/Linux/.test(ua) && !/Android/.test(ua)) return 'Linux'; " +
        "if (/Android/.test(ua)) return 'Android'; " +
        "if (/iPhone|iPad/.test(ua)) return 'iOS'; return null; }"
)
private external fun jsOsName(): String?

@JsFun(
    "() => { var ua = navigator.userAgent; " +
        "if (/Edg[/]/.test(ua)) return 'edge'; " +
        "if (/Chrome[/]/.test(ua) && !/Edg[/]/.test(ua)) return 'chrome'; " +
        "if (/Firefox[/]/.test(ua)) return 'firefox'; " +
        "if (/Safari[/]/.test(ua) && !/Chrome[/]/.test(ua)) return 'safari'; return null; }"
)
private external fun jsBrowserName(): String?

actual class DeviceContext actual constructor() {
    actual val platform: String = "wasmJs"
    actual val language: String? = jsLanguage()
    actual val appVersion: String? = null
    actual val screenWidth: Int? = jsScreenWidth()
    actual val screenHeight: Int? = jsScreenHeight()
    actual val userAgent: String? = jsUserAgent()
    actual val osName: String? = jsOsName()
    actual val osVersion: String? = null
    actual val browserName: String? = jsBrowserName()
    actual val browserVersion: String? = null
    actual val deviceModel: String? = null
    actual val anonymousId: String = generateAnonymousId()
}
