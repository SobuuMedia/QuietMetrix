package com.quietmetrix.dashboard.viewmodel

import com.quietmetrix.dashboard.api.ApiManagedUser
import com.quietmetrix.dashboard.api.ApiMember
import com.quietmetrix.dashboard.api.ApiProject
import com.quietmetrix.dashboard.api.UserRole

/** Access value for one person/project cell. */
internal enum class AccessState { Granted, NotGranted, Loading }

/** One person row and its project-keyed cells. */
internal data class AccessMatrixRow(val user: ApiManagedUser, val accessByProject: Map<String, AccessState>)

/** Builds a deterministic person-by-project matrix from the existing users and membership reads. */
internal fun buildAccessMatrix(
    users: List<ApiManagedUser>,
    projects: List<ApiProject>,
    membersByProject: Map<String, List<ApiMember>>,
): List<AccessMatrixRow> = users.map { user ->
    AccessMatrixRow(user, projects.associate { project ->
        val members = membersByProject[project.id]
        project.id to when {
            project.ownerUserId == user.id -> AccessState.Granted
            members == null -> AccessState.Loading
            members.any { it.userId == user.id } -> AccessState.Granted
            else -> AccessState.NotGranted
        }
    })
}

/** Prevents a matrix toggle from removing the signed-in user's own project membership. */
internal fun canToggleProjectAccess(userId: String, currentUserId: String?, isOwner: Boolean = false): Boolean =
    userId != currentUserId && !isOwner

/** The server protects its final global administrator on role changes and user deletion. */
internal fun canRemoveAdminRole(user: ApiManagedUser, users: List<ApiManagedUser>): Boolean =
    user.role != UserRole.ADMIN || users.count { it.role == UserRole.ADMIN } > 1
