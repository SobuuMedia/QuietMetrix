package com.quietmetrix.analytics.internal.transport

import java.net.InetAddress

actual class ConnectivityMonitor actual constructor() {
    actual val isOnline: Boolean
        get() = checkOnline()

    private fun checkOnline(): Boolean {
        return try {
            InetAddress.getByName("cloud.quietmetrix.com") != null
        } catch (_: Exception) {
            false
        }
    }

    actual fun onConnectivityChange(callback: (Boolean) -> Unit) {}
    actual fun close() {}
}