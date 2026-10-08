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
import platform.posix.rename
import platform.posix.errno
import platform.posix.ENOENT

internal actual fun createPersistentStore(prefix: String): PersistentStore = FileBasedPersistentStore(prefix)

@OptIn(ExperimentalForeignApi::class)
internal class FileBasedPersistentStore(private val prefix: String) : PersistentStore {
    private val dir: String = (getenv("HOME")?.toKString() ?: ".") + "/.quietmetrix"

    init {
        // 0x1FF == 0777; umask restricts to the user's default permissions.
        mkdir(dir, 0x1FFu)
    }

    private fun pathFor(key: String) = "$dir/$prefix$key"

    override fun get(key: String): String? {
        val file = fopen(pathFor(key), "rb") ?: run {
            check(errno == ENOENT) { "Could not read QuietMetrix state file" }
            return null
        }
        try {
            fseek(file, 0L, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0L, SEEK_SET)
            if (size <= 0) return ""
            val buffer = ByteArray(size)
            val read = buffer.usePinned { fread(it.addressOf(0), 1.convert(), size.convert(), file) }
            return buffer.decodeToString(0, read.toInt())
        } finally {
            fclose(file)
        }
    }

    override fun set(key: String, value: String) {
        val destination = pathFor(key)
        val temporary = "$destination.tmp"
        val file = fopen(temporary, "wb") ?: error("Could not open QuietMetrix state file")
        try {
            val bytes = value.encodeToByteArray()
            if (bytes.isNotEmpty()) {
                val written = bytes.usePinned { fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), file) }
                check(written.toLong() == bytes.size.toLong()) { "Could not write QuietMetrix state file" }
            }
        } finally {
            fclose(file)
        }
        check(rename(temporary, destination) == 0) { "Could not replace QuietMetrix state file" }
    }

    override fun remove(key: String) {
        remove(pathFor(key))
    }
}
