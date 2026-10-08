package com.quietmetrix.server.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperimentStatusTest {

    /** Six persisted statuses include resolution of finished experiments. */
    @Test
    fun `six statuses support explicit resolution`() {
        assertEquals(setOf("draft", "active", "paused", "ended", "shipped", "closed"), ExperimentStatus.ALL)
        for (from in listOf("active", "paused", "ended")) {
            assertTrue(ExperimentStatus.canTransition(from, "shipped"))
            assertTrue(ExperimentStatus.canTransition(from, "closed"))
        }
    }

    @Test
    fun `draft can move to active or ended, not to paused or to itself`() {
        assertTrue(ExperimentStatus.canTransition("draft", "active"))
        assertTrue(ExperimentStatus.canTransition("draft", "ended"))
        assertFalse(ExperimentStatus.canTransition("draft", "paused"))
        assertFalse(ExperimentStatus.canTransition("draft", "draft"))
    }

    @Test
    fun `active can move to paused or ended, not back to draft or to itself`() {
        assertTrue(ExperimentStatus.canTransition("active", "paused"))
        assertTrue(ExperimentStatus.canTransition("active", "ended"))
        assertFalse(ExperimentStatus.canTransition("active", "draft"))
        assertFalse(ExperimentStatus.canTransition("active", "active"))
    }

    @Test
    fun `paused can move to active or ended, not back to draft or to itself`() {
        assertTrue(ExperimentStatus.canTransition("paused", "active"))
        assertTrue(ExperimentStatus.canTransition("paused", "ended"))
        assertFalse(ExperimentStatus.canTransition("paused", "draft"))
        assertFalse(ExperimentStatus.canTransition("paused", "paused"))
    }

    @Test
    fun `ended can be resolved and rollout terminals cannot move`() {
        for (to in ExperimentStatus.ALL) {
            assertEquals(to in setOf("shipped", "closed"), ExperimentStatus.canTransition("ended", to))
            assertFalse(ExperimentStatus.canTransition("shipped", to))
            assertFalse(ExperimentStatus.canTransition("closed", to))
        }
    }

    @Test
    fun `draft allows editing everything, active and paused allow only name screen and traffic, ended allows nothing`() {
        assertEquals(setOf("name", "screen", "traffic_percent", "variants", "countries", "mode", "placement_key", "b_percent", "languages", "match_mode", "goal_event", "icon_key"), ExperimentStatus.editableFields("draft"))
        assertEquals(setOf("name", "screen", "traffic_percent"), ExperimentStatus.editableFields("active"))
        assertEquals(setOf("name", "screen", "traffic_percent"), ExperimentStatus.editableFields("paused"))
        assertTrue(ExperimentStatus.editableFields("ended").isEmpty())
    }
}
