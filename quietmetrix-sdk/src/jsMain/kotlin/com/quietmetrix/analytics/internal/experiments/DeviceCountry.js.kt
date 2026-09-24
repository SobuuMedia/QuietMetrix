package com.quietmetrix.analytics.internal.experiments

// Guards on `typeof` so this works under SSR / plain Node without throwing, matching
// DeviceContext.js.kt's jsLanguage(). navigator.language is e.g. "es-ES" -> region "ES";
// "es" (no region) -> null, left to normalizeRegion() to reject if malformed.
private fun jsRegion(): String? =
    js(
        "(function(){ if (typeof navigator === 'undefined' || !navigator.language) return null;" +
            " var parts = navigator.language.split('-'); return parts.length > 1 ? parts[1] : null; })()",
    )

internal actual fun deviceCountry(): String? = normalizeRegion(jsRegion())
