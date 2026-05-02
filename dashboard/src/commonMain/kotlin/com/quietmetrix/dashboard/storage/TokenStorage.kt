package com.quietmetrix.dashboard.storage

expect object TokenStorage {
    fun save(token: String)
    fun get(): String?
    fun clear()
    fun saveRefresh(token: String)
    fun getRefresh(): String?
    fun clearRefresh()
}
