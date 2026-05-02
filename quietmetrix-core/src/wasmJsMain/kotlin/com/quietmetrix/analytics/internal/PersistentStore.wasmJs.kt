package com.quietmetrix.analytics.internal

internal actual fun createPersistentStore(prefix: String): PersistentStore = LocalStoragePersistentStore(prefix)

internal class LocalStoragePersistentStore(private val prefix: String) : PersistentStore {
    override fun get(key: String): String? = runCatching {
        js("localStorage.getItem('${prefix}${key}')") as? String
    }.getOrNull()
    override fun set(key: String, value: String) {
        js("localStorage.setItem('${prefix}${key}', '${value}')")
    }
    override fun remove(key: String) {
        js("localStorage.removeItem('${prefix}${key}')")
    }
}
