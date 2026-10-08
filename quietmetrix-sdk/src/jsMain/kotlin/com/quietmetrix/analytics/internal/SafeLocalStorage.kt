package com.quietmetrix.analytics.internal

/**
 * localStorage accessor that degrades gracefully when the browser API is unavailable
 * (e.g. server-side rendering with Vue/Nuxt or React SSR, or a plain Node process). In a real
 * browser it uses durable `localStorage`; non-browser hosts use [InMemoryStore].
 * Browser storage errors become Kotlin failures so consent gates can fail closed.
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

private fun available(): Boolean {
    val result: dynamic = js("(() => { try { return typeof localStorage !== 'undefined' && localStorage !== null; } catch (error) { return null; } })()")
    check(result != null) { "Browser storage unavailable" }
    return result as Boolean
}

private fun rawGet(key: String): String? {
    val result: dynamic = js("(() => { try { return localStorage.getItem(key); } catch (error) { return false; } })()")
    check(result !== false) { "Browser storage unavailable" }
    return result as String?
}

private fun rawSet(key: String, value: String) {
    val succeeded: Boolean = js("(() => { try { localStorage.setItem(key, value); return true; } catch (error) { return false; } })()")
    check(succeeded) { "Browser storage unavailable" }
}

private fun rawRemove(key: String) {
    val succeeded: Boolean = js("(() => { try { localStorage.removeItem(key); return true; } catch (error) { return false; } })()")
    check(succeeded) { "Browser storage unavailable" }
}
