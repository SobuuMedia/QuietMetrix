package com.quietmetrix.analytics

import com.quietmetrix.analytics.internal.counters.FrictionCounterBridge
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRect
import platform.Foundation.NSCoder
import platform.UIKit.UIEvent
import platform.UIKit.UITouch
import platform.UIKit.UITouchPhase
import platform.UIKit.UIWindow

private val frictionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

/**
 * A `UIWindow` subclass that feeds tap-down coordinates into [FrictionCounterBridge] for
 * automatic rage-tap detection. **Not automatic like Android's** — there is no safe way to
 * intercept touches on iOS without either this opt-in subclass or fragile Objective-C method
 * swizzling, which this SDK deliberately does not do. The app must use `QuietMetrixWindow` in
 * place of a plain `UIWindow` in its `SceneDelegate`/`AppDelegate` — see docs/sdk/ios.md. An
 * app that skips this integration step simply never produces `friction` counters; every other
 * QuietMetrix feature is unaffected.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class QuietMetrixWindow : UIWindow {
    @OverrideInit
    constructor(frame: CValue<CGRect>) : super(frame)

    @OverrideInit
    constructor(coder: NSCoder) : super(coder)

    override fun sendEvent(event: UIEvent) {
        val touches = event.allTouches
        if (touches != null) {
            for (touchAny in touches) {
                val touch = touchAny as? UITouch ?: continue
                if (touch.phase != UITouchPhase.UITouchPhaseBegan) continue
                val point = touch.locationInView(null)
                val (x, y) = point.useContents { this.x to this.y }
                val timestampMs = (touch.timestamp * 1000.0).toLong()
                frictionScope.launch { FrictionCounterBridge.onTap(x.toFloat(), y.toFloat(), timestampMs) }
            }
        }
        super.sendEvent(event)
    }
}
