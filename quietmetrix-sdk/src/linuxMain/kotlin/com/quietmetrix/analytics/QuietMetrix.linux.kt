package com.quietmetrix.analytics

internal actual fun platformInit(config: QuietMetrixConfig) {
    // No-op on Linux
}

internal actual fun platformStop() = Unit
