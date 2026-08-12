package com.quietmetrix.analytics.internal.context

import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.StorageKeys
import com.quietmetrix.analytics.internal.createPersistentStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityUtilsTest {

    @AfterTest
    fun tearDown() {
        ConfigHolder.reset()
    }

    @Test
    fun generateAnonymousId_whenCollectionEnabled_returnsNonBlankId() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "idtest1_", collectAnonymousId = true))

        val id = generateAnonymousId()

        assertTrue(id.isNotBlank())
        assertTrue(id.startsWith("qm_aid_"))
    }

    @Test
    fun generateAnonymousId_whenCollectionDisabled_returnsBlank() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "idtest2_", collectAnonymousId = false))

        val id = generateAnonymousId()

        assertEquals("", id)
    }

    @Test
    fun generateAnonymousId_whenCollectionDisabled_neverPersistsAnId() {
        val prefix = "idtest3_"
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = prefix, collectAnonymousId = false))

        generateAnonymousId()

        val key = StorageKeys.anonymousId(prefix)
        assertNull(InMemoryStore.get(key))
        assertNull(createPersistentStore(prefix).get(key))
    }

    @Test
    fun generateAnonymousId_whenCollectionEnabled_isStableAcrossCalls() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "idtest4_", collectAnonymousId = true))

        val first = generateAnonymousId()
        val second = generateAnonymousId()

        assertEquals(first, second)
    }
}
