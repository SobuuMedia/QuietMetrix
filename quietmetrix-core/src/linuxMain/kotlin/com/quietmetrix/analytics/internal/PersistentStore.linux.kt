package com.quietmetrix.analytics.internal

internal actual fun createPersistentStore(prefix: String): PersistentStore = FileBasedPersistentStore(prefix)

internal class FileBasedPersistentStore(private val prefix: String) : PersistentStore {
    private val dir = java.io.File(System.getProperty("user.home"), ".quietmetrix")
    private fun fileFor(key: String) = java.io.File(dir, "${prefix}${key}")
    init { dir.mkdirs() }
    override fun get(key: String): String? = runCatching { fileFor(key).readText() }.getOrNull()
    override fun set(key: String, value: String) = fileFor(key).writeText(value)
    override fun remove(key: String) = fileFor(key).delete()
}
