package com.quietmetrix.analytics.internal.experiments

// Kotlin/Wasm requires js(...) calls to be the sole expression of a top-level function body,
// so this is a @JsFun external, matching DeviceContext.wasmJs.kt's jsLanguage(). No `typeof`
// guard, same as jsLanguage() there — wasmJs assumes a browser environment.
@JsFun("() => { var parts = (navigator.language || '').split('-'); return parts.length > 1 ? parts[1] : null; }")
private external fun jsRegion(): String?

internal actual fun deviceCountry(): String? = normalizeRegion(jsRegion())
