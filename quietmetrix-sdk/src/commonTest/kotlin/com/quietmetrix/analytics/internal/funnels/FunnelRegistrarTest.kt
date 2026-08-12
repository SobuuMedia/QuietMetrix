package com.quietmetrix.analytics.internal.funnels

import com.quietmetrix.analytics.Funnel
import com.quietmetrix.analytics.FunnelManifest
import com.quietmetrix.analytics.FunnelStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [FunnelRegistrar.canonicalPayload] is the change-detection fingerprint compared across app
 * launches — SDK registration should fire once per meaningful change, not once per launch and
 * not spuriously when nothing changed. These tests exercise that pure function directly,
 * without touching the network (mirrors how EventNormalizer/EventValidator keep pure logic
 * separate from I/O for testability).
 */
class FunnelRegistrarTest {

    private fun funnel(
        key: String = "signup",
        steps: List<FunnelStep> = listOf(FunnelStep(key = "view", event = "screen_view"), FunnelStep(key = "submit", event = "signup_submitted")),
        windowSeconds: Long = 604_800L,
    ) = Funnel(key = key, name = "Signup", steps = steps, windowSeconds = windowSeconds)

    @Test
    fun `identical funnel lists produce the same payload`() {
        val a = FunnelRegistrar.canonicalPayload(listOf(funnel()))
        val b = FunnelRegistrar.canonicalPayload(listOf(funnel()))
        assertEquals(a, b)
    }

    @Test
    fun `declaration order does not affect the payload`() {
        val a = FunnelRegistrar.canonicalPayload(listOf(funnel(key = "a"), funnel(key = "b")))
        val b = FunnelRegistrar.canonicalPayload(listOf(funnel(key = "b"), funnel(key = "a")))
        assertEquals(a, b)
    }

    @Test
    fun `a changed step event changes the payload`() {
        val a = FunnelRegistrar.canonicalPayload(listOf(funnel()))
        val b = FunnelRegistrar.canonicalPayload(listOf(funnel(steps = listOf(FunnelStep(key = "view", event = "different_event")))))
        assertNotEquals(a, b)
    }

    @Test
    fun `a changed window changes the payload`() {
        val a = FunnelRegistrar.canonicalPayload(listOf(funnel(windowSeconds = 3600L)))
        val b = FunnelRegistrar.canonicalPayload(listOf(funnel(windowSeconds = 7200L)))
        assertNotEquals(a, b)
    }

    @Test
    fun `a manifest revision changes the payload`() {
        val a = FunnelRegistrar.canonicalPayload(FunnelManifest("app", 1, listOf(funnel())))
        val b = FunnelRegistrar.canonicalPayload(FunnelManifest("app", 2, listOf(funnel())))
        assertNotEquals(a, b)
    }

    @Test
    fun `an empty funnel list has a stable payload`() {
        assertEquals(FunnelRegistrar.canonicalPayload(emptyList()), FunnelRegistrar.canonicalPayload(emptyList()))
    }

    @Test
    fun `shouldRegister is true when there is no stored fingerprint`() {
        assertEquals(true, FunnelRegistrar.shouldRegister("payload", null))
    }

    @Test
    fun `shouldRegister is false when the payload matches the stored fingerprint`() {
        assertEquals(false, FunnelRegistrar.shouldRegister("payload", "payload"))
    }

    @Test
    fun `shouldRegister is true when the payload differs from the stored fingerprint`() {
        assertEquals(true, FunnelRegistrar.shouldRegister("payload-v2", "payload-v1"))
    }
}
