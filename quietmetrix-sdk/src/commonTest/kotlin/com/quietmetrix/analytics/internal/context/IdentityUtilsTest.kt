package com.quietmetrix.analytics.internal.context

import com.quietmetrix.analytics.QuietMetrixConfig
import com.quietmetrix.analytics.internal.ConfigHolder
import com.quietmetrix.analytics.internal.InMemoryStore
import com.quietmetrix.analytics.internal.StorageKeys
import com.quietmetrix.analytics.internal.createPersistentStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
class IdentityUtilsTest {

    @AfterTest
    fun tearDown() {
        ConfigHolder.reset()
    }

    @Test
    fun generateAnonymousId_legacyOptInIsIgnored() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "idtest1_", collectAnonymousId = true))

        val id = generateAnonymousId()

        assertEquals("", id)
        assertNull(InMemoryStore.get(StorageKeys.anonymousId("idtest1_")))
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
    fun generateAnonymousId_isAlwaysBlank() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "idtest4_", collectAnonymousId = true))

        val first = generateAnonymousId()
        val second = generateAnonymousId()

        assertEquals(first, second)
        assertEquals("", first)
    }

    @Test
    fun clearLegacyAnonymousId_removesPreviouslyPersistedValue() {
        val prefix = "oldid_"
        val key = StorageKeys.anonymousId(prefix)
        InMemoryStore.set(key, "qm_aid_legacy")
        createPersistentStore(prefix).set(key, "qm_aid_legacy")

        clearLegacyAnonymousId(QuietMetrixConfig(storageKeyPrefix = prefix))

        assertNull(InMemoryStore.get(key))
        assertNull(createPersistentStore(prefix).get(key))
    }

    @Test
    fun loadOrCreateBucketingId_isStableAcrossCalls() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "bkttest1_", collectAnonymousId = true))

        val first = loadOrCreateBucketingId()
        val second = loadOrCreateBucketingId()

        assertEquals(first, second)
    }

    @Test
    fun loadOrCreateBucketingId_startsWithBktPrefix() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "bkttest2_", collectAnonymousId = true))

        val id = loadOrCreateBucketingId()

        assertNotNull(id)
        assertTrue(id.startsWith("qm_bkt_"))
    }

    @Test
    fun loadOrCreateBucketingId_isCreatedRegardlessOfCollectAnonymousId() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "bkttest3_", collectAnonymousId = false))

        val id = loadOrCreateBucketingId()

        assertNotNull(id)
        assertTrue(id.isNotBlank())
    }

    @Test
    fun loadOrCreateBucketingId_differsFromTheAnonymousId() {
        ConfigHolder.set(QuietMetrixConfig(storageKeyPrefix = "bkttest4_", collectAnonymousId = true))

        val anonymousId = generateAnonymousId()
        val bucketingId = loadOrCreateBucketingId()

        assertTrue(anonymousId != bucketingId, "bucketingId must be a separate identifier, not an alias of the anonymous id")
    }

    @Test
    fun loadOrCreateBucketingId_isNullBeforeInit() {
        assertNull(loadOrCreateBucketingId())
    }
}
