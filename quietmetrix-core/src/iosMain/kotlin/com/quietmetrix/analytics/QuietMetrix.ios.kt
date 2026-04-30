package com.quietmetrix.analytics

internal actual fun platformInit(config: QuietMetrixConfig) {
    // No-op on iOS stub; consent/banner state lives in InMemoryStore.
    // A future revision can add an NSUserDefaults-backed store.
}
