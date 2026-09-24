package com.quietmetrix.analytics.internal.context

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.StorageKeys
import com.quietmetrix.analytics.internal.createPersistentStore

internal fun generateAnonymousId(): String {
    val config = ConfigHolder.configOrNull ?: return ""
    if (!config.collectAnonymousId) return ""
    val key = StorageKeys.anonymousId(config.storageKeyPrefix)

    // Check in-memory cache first
    val cached = InMemoryStore.get(key)
    if (cached != null) return cached

    // Check persistent store (survives restarts)
    val store = createPersistentStore(config.storageKeyPrefix)
    val persisted = store.get(key)
    if (persisted != null) {
        InMemoryStore.set(key, persisted)
        return persisted
    }

    // Generate new ID and persist it
    val newId = "qm_aid_" + kotlin.random.Random.nextBytes(16).joinToString("") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
    store.set(key, newId)
    InMemoryStore.set(key, newId)
    return newId
}

/**
 * A random id generated once per install, persisted locally, and used ONLY to compute A/B
 * testing variant assignment (`hash(experimentKey + bucketingId) % totalWeight`) on-device —
 * it is never included in any network payload (unlike [generateAnonymousId], which is sent to
 * the server when [com.quietmetrix.analytics.QuietMetrixConfig.collectAnonymousId] is true).
 * Generated regardless of that setting, so a customer who disables anonymous-id collection —
 * a privacy-conscious, product-encouraged choice — doesn't lose deterministic experiment
 * assignment: without a separate id, every device would hash on the same empty string and
 * land in the same variant. Returns null only when the SDK hasn't been initialized yet.
 */
internal fun loadOrCreateBucketingId(): String? {
    val config = ConfigHolder.configOrNull ?: return null
    val key = StorageKeys.bucketingId(config.storageKeyPrefix)

    val cached = InMemoryStore.get(key)
    if (cached != null) return cached

    val store = createPersistentStore(config.storageKeyPrefix)
    val persisted = store.get(key)
    if (persisted != null) {
        InMemoryStore.set(key, persisted)
        return persisted
    }

    val newId = "qm_bkt_" + kotlin.random.Random.nextBytes(16).joinToString("") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
    store.set(key, newId)
    InMemoryStore.set(key, newId)
    return newId
}
