package com.quietmetrix.dashboard.storage

import kotlinx.browser.window

actual object TokenStorage {
    actual fun save(token: String) {
        window.localStorage.setItem("qm_auth_token", token)
    }

    actual fun get(): String? {
        return window.localStorage.getItem("qm_auth_token")
    }

    actual fun clear() {
        window.localStorage.removeItem("qm_auth_token")
    }

    actual fun saveRefresh(token: String) {
        window.localStorage.setItem("qm_refresh_token", token)
    }

    actual fun getRefresh(): String? {
        return window.localStorage.getItem("qm_refresh_token")
    }

    actual fun clearRefresh() {
        window.localStorage.removeItem("qm_refresh_token")
    }

    actual fun saveUser(json: String) {
        window.localStorage.setItem("qm_user", json)
    }

    actual fun getUser(): String? {
        return window.localStorage.getItem("qm_user")
    }

    actual fun clearUser() {
        window.localStorage.removeItem("qm_user")
    }

    actual fun saveLastProjectId(projectId: String) {
        window.localStorage.setItem("qm_last_project_id", projectId)
    }

    actual fun getLastProjectId(): String? {
        return window.localStorage.getItem("qm_last_project_id")
    }

    actual fun clearLastProjectId() {
        window.localStorage.removeItem("qm_last_project_id")
    }
}
