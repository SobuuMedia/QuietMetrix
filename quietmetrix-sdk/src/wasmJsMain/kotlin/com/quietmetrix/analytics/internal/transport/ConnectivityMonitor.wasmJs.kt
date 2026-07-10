package com.quietmetrix.analytics.internal.transport

@JsFun("navigator.onLine === true")
external fun jsIsOnline(): Boolean

@JsFun("window.addEventListener('online', function() { callback(true) }); window.addEventListener('offline', function() { callback(false) })")
external fun jsRegisterConnectivityCallbacks(callback: (Boolean) -> Unit)

actual class ConnectivityMonitor actual constructor() {
    actual val isOnline: Boolean
        get() = jsIsOnline()

    actual fun onConnectivityChange(callback: (Boolean) -> Unit) {
        jsRegisterConnectivityCallbacks(callback)
    }

    actual fun close() {}
}
