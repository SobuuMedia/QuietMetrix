package com.quietmetrix.dashboard.storage

import java.util.concurrent.ConcurrentHashMap

// JVM actual for the test-only target. The production dashboard ships as
// wasmJs and uses window.localStorage there; on the JVM we keep everything
// in a process-local map so pure-logic unit tests can run without a browser.
actual object TokenStorage {
    private val store = ConcurrentHashMap<String, String>()

    private const val KEY_TOKEN = "qm_auth_token"
    private const val KEY_REFRESH = "qm_refresh_token"
    private const val KEY_LAST_PROJECT = "qm_last_project_id"
    private const val KEY_USER = "qm_user"

    actual fun save(token: String) { store[KEY_TOKEN] = token }
    actual fun get(): String? = store[KEY_TOKEN]
    actual fun clear() { store.remove(KEY_TOKEN) }

    actual fun saveRefresh(token: String) { store[KEY_REFRESH] = token }
    actual fun getRefresh(): String? = store[KEY_REFRESH]
    actual fun clearRefresh() { store.remove(KEY_REFRESH) }

    actual fun saveUser(json: String) { store[KEY_USER] = json }
    actual fun getUser(): String? = store[KEY_USER]
    actual fun clearUser() { store.remove(KEY_USER) }

    actual fun saveLastProjectId(projectId: String) { store[KEY_LAST_PROJECT] = projectId }
    actual fun getLastProjectId(): String? = store[KEY_LAST_PROJECT]
    actual fun clearLastProjectId() { store.remove(KEY_LAST_PROJECT) }
}
