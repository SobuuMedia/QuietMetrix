package com.quietmetrix.analytics.internal

/**
 * localStorage accessor that degrades gracefully when the browser API is unavailable
 * (e.g. server-side rendering with Vue/Nuxt or React SSR, or a plain Node process). In a real
 * browser it uses `localStorage`; otherwise it falls back to [InMemoryStore] — the same
 * process-local store the JVM/Android/iOS targets use — so consent/persistence behave
 * consistently off the main browser thread and never throw a ReferenceError.
 */
internal object SafeLocalStorage {
    fun get(key: String): String? =
        if (available()) rawGet(key) else InMemoryStore.get(key)

    fun set(key: String, value: String) {
        if (available()) rawSet(key, value) else InMemoryStore.set(key, value)
    }

    fun remove(key: String) {
        if (available()) rawRemove(key) else InMemoryStore.remove(key)
    }

    fun has(key: String): Boolean =
        if (available()) rawGet(key) != null else InMemoryStore.has(key)
}

private fun available(): Boolean =
    js("typeof localStorage !== 'undefined' && localStorage !== null")

private fun rawGet(key: String): String? =
    js("localStorage.getItem(key)")

private fun rawSet(key: String, value: String) {
    js("localStorage.setItem(key, value)")
}

private fun rawRemove(key: String) {
    js("localStorage.removeItem(key)")
}
