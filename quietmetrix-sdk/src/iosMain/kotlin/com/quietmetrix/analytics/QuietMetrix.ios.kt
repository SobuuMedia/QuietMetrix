package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.ScreenTracker
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

internal actual fun platformInit(config: QuietMetrixConfig) {
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationWillEnterForegroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ -> QuietMetrix.onForeground() }
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ ->
        QuietMetrix.onBackground()
    }
}
