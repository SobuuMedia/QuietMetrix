package com.quietmetrix.analytics.internal

internal actual fun createPersistentStore(prefix: String): PersistentStore = FileBasedPersistentStore(prefix)

internal class FileBasedPersistentStore(private val prefix: String) : PersistentStore {
    private val dir = java.io.File(System.getProperty("user.home"), ".quietmetrix")
    private fun fileFor(key: String) = java.io.File(dir, "${prefix}${key}")
    init { dir.mkdirs() }
    override fun get(key: String): String? {
        val file = fileFor(key)
        return if (java.nio.file.Files.notExists(file.toPath())) null else file.readText()
    }
    override fun set(key: String, value: String) {
        val destination = fileFor(key)
        val temporary = java.nio.file.Files.createTempFile(dir.toPath(), "${prefix}${key}.", ".tmp").toFile()
        temporary.writeText(value)
        runCatching {
            java.nio.file.Files.move(
                temporary.toPath(), destination.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            java.nio.file.Files.move(
                temporary.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrThrow()
    }
    override fun remove(key: String) { fileFor(key).delete() }
}
