package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.ApiUser
import com.quietmetrix.dashboard.api.TimeRange
import com.quietmetrix.dashboard.api.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Task 5 — role-derived UI gating helpers on DashboardState. */
class DashboardStateRoleTest {

    private fun state(role: String) = DashboardState(user = ApiUser("1", "u@x.c", role))

    @Test
    fun adminCanDoEverything() {
        val s = state(UserRole.ADMIN)
        assertTrue(s.isAdmin)
        assertTrue(s.canRegenerate)
        assertFalse(s.isReadOnly)
    }

    @Test
    fun developerCanRegenerateButIsNotAdmin() {
        val s = state(UserRole.DEVELOPER)
        assertFalse(s.isAdmin)
        assertTrue(s.canRegenerate)
        assertFalse(s.isReadOnly)
    }

    @Test
    fun reviewerIsReadOnly() {
        val s = state(UserRole.REVIEWER)
        assertFalse(s.isAdmin)
        assertFalse(s.canRegenerate)
        assertTrue(s.isReadOnly)
    }

    @Test
    fun overviewDefaultsToSevenDayWindow() {
        assertEquals(TimeRange.Day7, DashboardState().range)
    }

    @Test
    fun noUserHasNoPrivileges() {
        val s = DashboardState()
        assertEquals(null, s.role)
        assertFalse(s.isAdmin)
        assertFalse(s.canRegenerate)
        assertFalse(s.isReadOnly)
    }
}
