package com.quietmetrix.analytics.internal.transport

// Default to online unless the browser explicitly reports offline. Environments without a
// real `navigator.onLine` (SSR, plain Node) are treated as online rather than offline.
private fun jsIsOnline(): Boolean =
    js("(typeof navigator === 'undefined') ? true : (navigator.onLine !== false)")

private fun jsRegisterConnectivityCallbacks(callback: (Boolean) -> Unit) {
    js(
        "if (typeof window !== 'undefined' && window.addEventListener) {" +
            "window.addEventListener('online', function() { callback(true) });" +
            "window.addEventListener('offline', function() { callback(false) }); }"
    )
}

actual class ConnectivityMonitor actual constructor() {
    actual val isOnline: Boolean
        get() = jsIsOnline()

    actual fun onConnectivityChange(callback: (online: Boolean) -> Unit) {
        jsRegisterConnectivityCallbacks(callback)
    }

    actual fun close() {}
}
