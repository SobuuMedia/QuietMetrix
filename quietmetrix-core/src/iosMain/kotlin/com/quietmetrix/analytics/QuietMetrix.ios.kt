package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ScreenTracker
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

internal actual fun platformInit(config: QuietMetrixConfig) {
    // Close out the current screen's dwell time when the app is backgrounded, so the final screen
    // the user was on is recorded even if they never navigate away. Consent/banner state lives in
    // InMemoryStore; a future revision can add an NSUserDefaults-backed store.
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ ->
        ScreenTracker.closeOutAsync()
    }
}
