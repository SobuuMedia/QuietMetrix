package com.quietmetrix.analytics.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.remove

internal actual fun createPersistentStore(prefix: String): PersistentStore = FileBasedPersistentStore(prefix)

@OptIn(ExperimentalForeignApi::class)
internal class FileBasedPersistentStore(private val prefix: String) : PersistentStore {
    private val home: String =
        getenv("USERPROFILE")?.toKString() ?: getenv("HOME")?.toKString() ?: "."
    private val dir: String = "$home\\.quietmetrix"

    init {
        // The Windows CRT mkdir takes a single path argument.
        mkdir(dir)
    }

    private fun pathFor(key: String) = "$dir\\$prefix$key"

    override fun get(key: String): String? {
        val file = fopen(pathFor(key), "rb") ?: return null
        try {
            // On Windows the CRT fseek offset is a 32-bit long (Kotlin Int).
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)
            if (size <= 0) return ""
            val buffer = ByteArray(size)
            val read = buffer.usePinned { fread(it.addressOf(0), 1.convert(), size.convert(), file) }
            return buffer.decodeToString(0, read.toInt())
        } finally {
            fclose(file)
        }
    }

    override fun set(key: String, value: String) {
        val file = fopen(pathFor(key), "wb") ?: return
        try {
            val bytes = value.encodeToByteArray()
            if (bytes.isNotEmpty()) {
                bytes.usePinned { fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), file) }
            }
        } finally {
            fclose(file)
        }
    }

    override fun remove(key: String) {
        remove(pathFor(key))
    }
}
