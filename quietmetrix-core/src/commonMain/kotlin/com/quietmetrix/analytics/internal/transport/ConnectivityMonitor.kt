package com.quietmetrix.analytics.internal.transport

expect class ConnectivityMonitor() {
    val isOnline: Boolean
    fun onConnectivityChange(callback: (online: Boolean) -> Unit)
    fun close()
}