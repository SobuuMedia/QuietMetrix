package com.quietmetrix.dashboard.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Task 7 — invite password validation rules. */
class InvitePasswordValidationTest {

    @Test
    fun shortPasswordIsRejected() {
        assertEquals(InvitePasswordError.TOO_SHORT, validateInvitePassword("short", "short"))
    }

    @Test
    fun mismatchIsRejected() {
        assertEquals(InvitePasswordError.MISMATCH, validateInvitePassword("longenough1", "different1"))
    }

    @Test
    fun lengthIsCheckedBeforeMatch() {
        // Both wrong: too-short wins so the user fixes length first.
        assertEquals(InvitePasswordError.TOO_SHORT, validateInvitePassword("abc", "xyz"))
    }

    @Test
    fun matchingLongPasswordIsAccepted() {
        assertNull(validateInvitePassword("password1", "password1"))
    }
}
