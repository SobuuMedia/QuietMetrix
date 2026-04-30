package com.quietmetrix.analytics

internal actual fun platformInit(config: QuietMetrixConfig) {
    // No-op on Android stub; consent/banner state lives in InMemoryStore.
    // A future revision can add a SharedPreferences-backed store via an Android-specific
    // initAndroid(context, config) overload.
}
