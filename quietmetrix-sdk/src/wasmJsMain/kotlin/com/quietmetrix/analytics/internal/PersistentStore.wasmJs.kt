package com.quietmetrix.analytics.internal

internal actual fun createPersistentStore(prefix: String): PersistentStore = LocalStoragePersistentStore(prefix)

internal class LocalStoragePersistentStore(private val prefix: String) : PersistentStore {
    private fun prefixed(key: String): String = prefix + key

    override fun get(key: String): String? {
        val value = readStoredValue(prefixed(key))
        check(value != STORAGE_UNAVAILABLE) { "Browser storage unavailable" }
        return value
    }

    override fun set(key: String, value: String) {
        check(writeStoredValue(prefixed(key), value)) { "Browser storage unavailable" }
    }

    override fun remove(key: String) {
        check(removeStoredValue(prefixed(key))) { "Browser storage unavailable" }
    }
}

private const val STORAGE_UNAVAILABLE = "__quietmetrix_storage_unavailable__"

// Native JavaScript errors must become Kotlin failures at the interop boundary.
@JsFun("(key) => { try { return localStorage.getItem(key); } catch (error) { return '__quietmetrix_storage_unavailable__'; } }")
private external fun readStoredValue(key: String): String?

@JsFun("(key, value) => { try { localStorage.setItem(key, value); return true; } catch (error) { return false; } }")
private external fun writeStoredValue(key: String, value: String): Boolean

@JsFun("(key) => { try { localStorage.removeItem(key); return true; } catch (error) { return false; } }")
private external fun removeStoredValue(key: String): Boolean
