package com.quietmetrix.analytics.internal.transport

actual class ConnectivityMonitor actual constructor() {
    actual val isOnline: Boolean = true
    actual fun onConnectivityChange(callback: (Boolean) -> Unit) {}
    actual fun close() {}
}