package com.quietmetrix.server

import com.quietmetrix.server.persistence.UserRepository

/**
 * Seeds an initial admin user on first run, mirroring the PHP backend's
 * bootstrap behaviour. New users default to the `admin` role, so the seeded
 * account has full access.
 *
 * Idempotent: does nothing if [email] or [password] is blank, or if a user with
 * [email] already exists. Returns true only when a new admin was created.
 */
fun seedAdminUser(userRepo: UserRepository, email: String?, password: String?): Boolean {
    if (email.isNullOrBlank() || password.isNullOrBlank()) return false
    if (userRepo.findByEmail(email) != null) return false
    userRepo.create(email, password)
    return true
}
