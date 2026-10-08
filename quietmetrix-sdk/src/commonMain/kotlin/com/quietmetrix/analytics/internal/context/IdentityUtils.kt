package com.quietmetrix.analytics.internal.context

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.StorageKeys
import com.quietmetrix.analytics.internal.createPersistentStore

internal fun generateAnonymousId(): String {
    return ""
}

/** Removes identifiers persisted by older SDK versions. */
internal fun clearLegacyAnonymousId(config: com.quietmetrix.analytics.QuietMetrixConfig) {
    val key = StorageKeys.anonymousId(config.storageKeyPrefix)
    InMemoryStore.remove(key)
    runCatching { createPersistentStore(config.storageKeyPrefix).remove(key) }
}

/**
 * A random id generated once per install, persisted locally, and used ONLY to compute A/B
 * testing variant assignment (`hash(experimentKey + bucketingId) % totalWeight`) on-device —
 * it is never included in any network payload. It is generated only for stable local experiment
 * assignment, so customers who
 * previously enabled anonymous-id collection don't lose deterministic experiment
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
