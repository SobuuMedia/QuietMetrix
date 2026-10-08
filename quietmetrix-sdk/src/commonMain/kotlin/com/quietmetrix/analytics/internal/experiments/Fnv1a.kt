package com.quietmetrix.analytics.internal.experiments

/** FNV-1a 32-bit hash, mirrored exactly by the shared vectors in Fnv1aTest — every platform
 *  must hash a given string to the identical bucket, or assignment desyncs across devices. */
internal fun fnv1a32(s: String): UInt {
    var hash: UInt = 0x811C9DC5u
    for (byte in s.encodeToByteArray()) {
        hash = hash xor (byte.toUInt() and 0xFFu)
        hash *= 0x01000193u
    }
    return hash
}

/** [fnv1a32] reduced to a bucket in `[0, 100)`. */
internal fun bucketOf(s: String): Int = (fnv1a32(s) % 100u).toInt()
