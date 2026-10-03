package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.ApiManagedUser
import com.quietmetrix.dashboard.api.ApiMember
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure access-matrix shaping and safety predicates. */
class AccessMatrixTest {
    @Test
    fun matrixContainsEveryUserAndProjectAndMarksUnloadedMemberships() {
        val people = listOf(ApiManagedUser("1", "one@example.com"), ApiManagedUser("2", "two@example.com"))
        val projects = listOf(ApiProject("p1", "One", ownerUserId = "1"), ApiProject("p2", "Two"))
        val matrix = buildAccessMatrix(people, projects, mapOf("p1" to listOf(ApiMember("2", "two@example.com"))))
        assertEquals(2, matrix.size)
        assertEquals(AccessState.Granted, matrix[0].accessByProject["p1"])
        assertEquals(AccessState.Loading, matrix[0].accessByProject["p2"])
        assertEquals(AccessState.Granted, matrix[1].accessByProject["p1"])
    }

    @Test
    fun ownMembershipIsLockedAndOnlyAdminCanBeDemoted() {
        val onlyAdmin = ApiManagedUser("1", "admin@example.com", UserRole.ADMIN)
        val reviewer = ApiManagedUser("2", "reviewer@example.com")
        assertFalse(canToggleProjectAccess("1", "1"))
        assertFalse(canToggleProjectAccess("3", null, isOwner = true))
        assertTrue(canToggleProjectAccess("2", "1"))
        assertFalse(canRemoveAdminRole(onlyAdmin, listOf(onlyAdmin, reviewer)))
        assertTrue(canRemoveAdminRole(onlyAdmin, listOf(onlyAdmin, onlyAdmin.copy(id = "3"))))
    }
}
