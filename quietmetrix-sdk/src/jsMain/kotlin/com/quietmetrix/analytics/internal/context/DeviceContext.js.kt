package com.quietmetrix.analytics.internal.context

// Browser lookups for the plain-JS (js(IR)) target. Each guards on `typeof` so the SDK is
// safe under SSR / plain Node (Vue-Nuxt, React SSR) where navigator/window are undefined —
// there it simply reports nulls instead of throwing.

private fun jsLanguage(): String? =
    js("(typeof navigator !== 'undefined' && navigator.language) ? navigator.language.split('-')[0] : null")

private fun jsScreenWidth(): Int? =
    js("(typeof window !== 'undefined' && typeof window.innerWidth === 'number') ? window.innerWidth : null")

private fun jsScreenHeight(): Int? =
    js("(typeof window !== 'undefined' && typeof window.innerHeight === 'number') ? window.innerHeight : null")

private fun jsUserAgent(): String? =
    js("(typeof navigator !== 'undefined' && navigator.userAgent) ? navigator.userAgent : null")

private fun jsOsName(): String? =
    js(
        "(function(){ if (typeof navigator === 'undefined') return null; var ua = navigator.userAgent || '';" +
            "if (/Windows/.test(ua)) return 'Windows';" +
            "if (/Mac OS X/.test(ua)) return 'macOS';" +
            "if (/Linux/.test(ua) && !/Android/.test(ua)) return 'Linux';" +
            "if (/Android/.test(ua)) return 'Android';" +
            "if (/iPhone|iPad/.test(ua)) return 'iOS'; return null; })()"
    )

private fun jsBrowserName(): String? =
    js(
        "(function(){ if (typeof navigator === 'undefined') return null; var ua = navigator.userAgent || '';" +
            "if (/Edg[/]/.test(ua)) return 'edge';" +
            "if (/Chrome[/]/.test(ua) && !/Edg[/]/.test(ua)) return 'chrome';" +
            "if (/Firefox[/]/.test(ua)) return 'firefox';" +
            "if (/Safari[/]/.test(ua) && !/Chrome[/]/.test(ua)) return 'safari'; return null; })()"
    )

actual class DeviceContext actual constructor() {
    actual val platform: String = "js"
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
