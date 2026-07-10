package com.quietmetrix.analytics

internal actual fun platformInit(config: QuietMetrixConfig) {
    // No-op on macOS; consent/banner state lives in InMemoryStore.
}