package com.quietmetrix.analytics.internal.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

actual class ConnectivityMonitor actual constructor() {
    private var connectivityManager: ConnectivityManager? = null
    private var callback: ((Boolean) -> Unit)? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    actual val isOnline: Boolean
        get() = checkOnline()

    private fun checkOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    actual fun onConnectivityChange(callback: (Boolean) -> Unit) {
        this.callback = callback
    }

    fun initialize(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { callback?.invoke(true) }
            override fun onLost(network: Network) { callback?.invoke(false) }
        }
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    actual fun close() {
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
    }
}