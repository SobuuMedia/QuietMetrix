package com.quietmetrix.analytics.internal

/** Consent and opt-out use the same durable platform primitives as the aggregate outbox. */
@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
internal object PersistentPreferences {
    private val failedWrites = kotlin.concurrent.atomics.AtomicReference<Set<String>>(emptySet())
    internal var factory: (String) -> PersistentStore = ::createPersistentStore
        set(value) { failedWrites.store(emptySet()); field = value }

    fun get(name: String): String? {
        val config = ConfigHolder.configOrNull ?: return null
        check("${config.storageKeyPrefix}:$name" !in failedWrites.load()) { "Preference could not be persisted" }
        return factory(config.storageKeyPrefix).get(name)
    }

    fun set(name: String, value: String) {
        val config = ConfigHolder.configOrNull ?: return
        val key = "${config.storageKeyPrefix}:$name"
        try {
            factory(config.storageKeyPrefix).set(name, value)
            updateFailures(key, failed = false)
        } catch (failure: Exception) {
            updateFailures(key, failed = true)
            throw failure
        }
    }
    private fun updateFailures(key: String, failed: Boolean) {
        while (true) {
            val previous = failedWrites.load()
            val next = if (failed) previous + key else previous - key
            if (failedWrites.compareAndSet(previous, next)) return
        }
    }

}
