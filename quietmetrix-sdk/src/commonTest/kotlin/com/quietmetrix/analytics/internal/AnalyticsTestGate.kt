package com.quietmetrix.analytics.internal

import com.quietmetrix.analytics.QuietMetrixConfig

internal fun grantAnalyticsForTest(prefix: String) {
    resetTestPreferences()
    ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = prefix, trackingAllowedByDefault = true))
}

internal fun resetAnalyticsTestGate() {
    ConfigHolder.reset()
}

internal fun resetTestPreferences() {
    InMemoryStore.clear()
    PersistentPreferences.factory = { prefix -> object : PersistentStore {
        override fun get(key: String): String? = InMemoryStore.get(prefix + key)
        override fun set(key: String, value: String) { InMemoryStore.set(prefix + key, value) }
        override fun remove(key: String) { InMemoryStore.remove(prefix + key) }
    } }
}
