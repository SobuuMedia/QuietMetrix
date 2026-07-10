package com.quietmetrix.analytics

internal actual fun platformInit(config: QuietMetrixConfig) {
    // No-op on JVM; consent/banner state lives in InMemoryStore.
}
