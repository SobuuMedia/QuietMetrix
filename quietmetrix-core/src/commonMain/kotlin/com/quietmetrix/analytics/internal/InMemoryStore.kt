package com.quietmetrix.analytics.internal

/**
 * Process-local key-value store used by the JVM/Android/iOS stub actuals so that the SDK
 * compiles and behaves consistently on every target out of the box. Real platform-backed
 * persistence (SharedPreferences, NSUserDefaults) can be layered on later without changing
 * the public API.
 */
internal object InMemoryStore {
    private val map = mutableMapOf<String, String>()

    fun get(key: String): String? = map[key]
    fun set(key: String, value: String) { map[key] = value }
    fun has(key: String): Boolean = map.containsKey(key)

    internal fun clear() { map.clear() }
}
