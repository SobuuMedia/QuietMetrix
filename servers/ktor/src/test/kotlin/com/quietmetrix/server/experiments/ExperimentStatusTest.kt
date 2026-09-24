package com.quietmetrix.server.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperimentStatusTest {

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
    fun `ended cannot move anywhere`() {
        for (to in ExperimentStatus.ALL) {
            assertFalse(ExperimentStatus.canTransition("ended", to), "ended -> $to must be false")
        }
    }

    @Test
    fun `draft allows editing everything, active and paused allow only name screen and traffic, ended allows nothing`() {
        assertEquals(setOf("name", "screen", "traffic_percent", "variants", "countries"), ExperimentStatus.editableFields("draft"))
        assertEquals(setOf("name", "screen", "traffic_percent"), ExperimentStatus.editableFields("active"))
        assertEquals(setOf("name", "screen", "traffic_percent"), ExperimentStatus.editableFields("paused"))
        assertTrue(ExperimentStatus.editableFields("ended").isEmpty())
    }
}
