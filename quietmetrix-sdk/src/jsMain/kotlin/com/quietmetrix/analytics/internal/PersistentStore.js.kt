package com.quietmetrix.analytics.internal

internal actual fun createPersistentStore(prefix: String): PersistentStore = LocalStoragePersistentStore(prefix)

internal class LocalStoragePersistentStore(private val prefix: String) : PersistentStore {
    private fun prefixed(key: String): String = prefix + key

    override fun get(key: String): String? = SafeLocalStorage.get(prefixed(key))

    override fun set(key: String, value: String) = SafeLocalStorage.set(prefixed(key), value)

    override fun remove(key: String) = SafeLocalStorage.remove(prefixed(key))
}
