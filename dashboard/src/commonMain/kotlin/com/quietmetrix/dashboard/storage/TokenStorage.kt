package com.quietmetrix.dashboard.storage

expect object TokenStorage {
    fun save(token: String)
    fun get(): String?
    fun clear()
    fun saveRefresh(token: String)
    fun getRefresh(): String?
    fun clearRefresh()
    /** Persisted signed-in user (JSON) so role/email survive a page reload. */
    fun saveUser(json: String)
    fun getUser(): String?
    fun clearUser()
    fun saveLastProjectId(projectId: String)
    fun getLastProjectId(): String?
    fun clearLastProjectId()
}
