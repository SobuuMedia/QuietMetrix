package com.quietmetrix.server.ratelimit

import com.quietmetrix.server.persistence.ProjectRepository
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuotaEnforcerTest {

    private val enforcer = QuotaEnforcer(mockk<ProjectRepository>(relaxed = true))

    @Test
    fun `self-host is always unlimited regardless of plan`() {
        assertNull(enforcer.experimentLimit(null, isCloud = false))
        assertNull(enforcer.experimentLimit("free", isCloud = false))
        assertNull(enforcer.experimentLimit("pro", isCloud = false))
    }

    @Test
    fun `cloud with no plan or the free plan is capped at 2`() {
        assertEquals(2, enforcer.experimentLimit(null, isCloud = true))
        assertEquals(2, enforcer.experimentLimit("free", isCloud = true))
    }

    @Test
    fun `cloud with any other plan is unlimited`() {
        assertNull(enforcer.experimentLimit("pro", isCloud = true))
        assertNull(enforcer.experimentLimit("business", isCloud = true))
    }
}
