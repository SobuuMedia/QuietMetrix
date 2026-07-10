package com.quietmetrix.analytics.internal

import android.content.Context

internal actual fun createPersistentStore(prefix: String): PersistentStore = AndroidPersistentStore(prefix)

internal class AndroidPersistentStore(private val prefix: String) : PersistentStore {
    private var context: Context? = null

    init {
        context = ConfigHolder.configOrNull?.applicationContext as? Context
    }

    private val prefs by lazy {
        requireNotNull(context) { "Application context must be provided for Android PersistentStore" }
        context!!.getSharedPreferences("${prefix}quietmetrix", Context.MODE_PRIVATE)
    }

    override fun get(key: String): String? = prefs.getString("$prefix$key", null)
    override fun set(key: String, value: String) = prefs.edit().putString("$prefix$key", value).apply()
    override fun remove(key: String) = prefs.edit().remove("$prefix$key").apply()
}
