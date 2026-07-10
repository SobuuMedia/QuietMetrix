package com.quietmetrix.analytics.internal.context

import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.StorageKeys
import com.quietmetrix.analytics.internal.createPersistentStore

internal fun generateAnonymousId(): String {
    val config = ConfigHolder.configOrNull ?: return ""
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
