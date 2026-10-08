package com.quietmetrix.analytics.internal

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileBasedPersistentStoreTest {
    @Test
    fun removeDeletesTheFileAndCanBeRepeated() {
        val store = FileBasedPersistentStore("file_remove_test_${Random.nextLong().toString(16)}_")
        try {
            store.set("value", "first")
            assertEquals("first", store.get("value"))
            store.set("value", "replacement")
            assertEquals("replacement", store.get("value"))
            store.remove("value")
            assertNull(store.get("value"))
            store.remove("value")
            assertNull(store.get("value"))
        } finally {
            store.remove("value")
        }
    }
}
