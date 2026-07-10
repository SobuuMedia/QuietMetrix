package com.quietmetrix.analytics.internal

import kotlinx.browser.localStorage

internal actual fun createPersistentStore(prefix: String): PersistentStore = LocalStoragePersistentStore(prefix)

internal class LocalStoragePersistentStore(private val prefix: String) : PersistentStore {
    private fun prefixed(key: String): String = prefix + key

    override fun get(key: String): String? = runCatching {
        localStorage.getItem(prefixed(key))
    }.getOrNull()

    override fun set(key: String, value: String) {
        localStorage.setItem(prefixed(key), value)
    }

    override fun remove(key: String) {
        localStorage.removeItem(prefixed(key))
    }
}
