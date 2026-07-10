package com.quietmetrix.analytics.internal

internal interface PersistentStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}

internal expect fun createPersistentStore(prefix: String): PersistentStore
