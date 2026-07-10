package com.quietmetrix.analytics.internal

import platform.Foundation.NSUserDefaults

internal actual fun createPersistentStore(prefix: String): PersistentStore = NSDefaultsPersistentStore(prefix)

internal class NSDefaultsPersistentStore(private val prefix: String) : PersistentStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun get(key: String): String? = defaults.stringForKey("${prefix}${key}")
    override fun set(key: String, value: String) = defaults.setObject(value, "${prefix}${key}")
    override fun remove(key: String) = defaults.removeObjectForKey("${prefix}${key}")
}
