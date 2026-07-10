package com.quietmetrix.dashboard.ui.screens

/** Why an invite password is not yet acceptable, or null when it is valid. */
enum class InvitePasswordError { TOO_SHORT, MISMATCH }

/** Minimum length must match the backend's invite-accept check (8 chars). */
const val INVITE_MIN_PASSWORD_LENGTH = 8

/**
 * Validates the password a new user sets when accepting an invitation. Returns
 * null when the password may be submitted, otherwise the reason it is rejected.
 */
fun validateInvitePassword(password: String, confirm: String): InvitePasswordError? = when {
    password.length < INVITE_MIN_PASSWORD_LENGTH -> InvitePasswordError.TOO_SHORT
    password != confirm -> InvitePasswordError.MISMATCH
    else -> null
}
