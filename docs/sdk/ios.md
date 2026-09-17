# SDK: iOS

> Setting this up for the first time? An AI agent can create the project and fill in the
> values below for you — see [Agent-driven setup](../agents/setup.md).

## Installation

### Swift Package Manager

Add the QuietMetrix package to your project via Xcode:

1. File → Add Package Dependencies
2. Enter the repository URL: `https://github.com/sobuumedia/quietmetrix-sdk-swift`
3. Select the latest version and add the `QuietMetrix` library target

Or add it to your `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/sobuumedia/quietmetrix-sdk-swift", from: "0.4.0"),
],
targets: [
    .target(name: "YourApp", dependencies: ["QuietMetrix"]),
]
```

### XCFramework (Manual)

If you prefer not to use SPM, download the `QuietMetrix.xcframework` from the [GitHub releases](https://github.com/sobuumedia/quietmetrix-sdk-swift/releases) page and drag it into your Xcode project's **Frameworks, Libraries, and Embedded Content** section.

### Objective-C

The Swift SDK is interoperable with Objective-C. Import the framework header:

```objc
@import QuietMetrix;
```

## Initialization

Initialize in your `AppDelegate` or `SceneDelegate`:

```swift
import QuietMetrix

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let config = QuietMetrixConfig(
            storageKeyPrefix: "myapp_",
            trackingEndpoint: "https://your-server.com/api/v1",
            apiKey: "qm_ak_your_api_key",
            flushIntervalMs: 30_000,
        )
        QuietMetrix.shared.initialize(config: config)
        return true
    }
}
```

## Track Events

```swift
QuietMetrix.shared.trackEvent(event: "button_click", screen: "settings")
QuietMetrix.shared.trackEvent(event: "page_view", screen: "home", props: ["tab": "featured"])
```

### Objective-C

```objc
[[QuietMetrix shared] trackEventWithEvent:@"button_click" screen:@"settings" props:nil];
[[QuietMetrix shared] trackEventWithEvent:@"page_view" screen:@"home" props:@{@"tab": @"featured"}];
```

## Funnels

Declare funnels when building your config and they auto-register with the server — no
dashboard setup required. See the [Funnels guide](funnels.md) for the full concept, matching
rules, and worked example.

```swift
let signupFunnel = Funnel(
    key: "signup",
    name: "Signup",
    steps: [
        FunnelStep(key: "view", event: "screen_view", screen: "signup"),
        FunnelStep(key: "submit", event: "signup_submitted"),
    ]
)

let config = QuietMetrixConfig(
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1",
    apiKey: "qm_ak_your_api_key",
    funnels: [signupFunnel]
)
QuietMetrix.shared.initialize(config: config)
```

## Consent

```swift
// After user accepts cookie consent
QuietMetrix.shared.setCookieConsent(true)

// Master kill switch — separate from cookie consent
QuietMetrix.shared.setAnalyticsEnabled(false)

// Check consent state
if QuietMetrix.shared.isTrackingAllowed() {
    // Safe to track
}
```

The consent state is persisted in `UserDefaults` and survives app restarts.

## Force Flush

Events are automatically flushed on the configured interval. To flush immediately (e.g., when the app enters the background):

```swift
func applicationDidEnterBackground(_ application: UIApplication) {
    QuietMetrix.shared.flush()
}
```

## Offline Support

Pending counters are in-memory only — there is no offline buffer, no connectivity detection, and no retry backoff. A flush that fails (offline, 5xx, timeout) simply drops that batch rather than queuing it; the next scheduled flush tries again with whatever has accumulated since. An app killed between flushes loses whatever was recorded since the last successful one.

## Friction (Rage-tap Detection)

QuietMetrix can automatically detect "rage taps" — repeated fast taps in roughly the same spot, usually a sign the user is stuck or the UI didn't respond — and report them as a `friction` counter, broken down by screen. **Unlike Android, this is not automatic on iOS**: there is no safe way to intercept touches without either an opt-in window subclass or fragile Objective-C method swizzling, which this SDK deliberately does not do.

To enable it, use `QuietMetrixWindow` in place of a plain `UIWindow` wherever your app creates its window — typically your `SceneDelegate`:

```swift
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        window = QuietMetrixWindow(windowScene: windowScene)
        // ... set rootViewController, etc.
        window?.makeKeyAndVisible()
    }
}
```

Skipping this step doesn't break anything — every other QuietMetrix feature works identically — `friction` counters simply never appear from iOS. Android requires no equivalent step; its tap capture is fully automatic.

## App Tracking Transparency

QuietMetrix does not use IDFA and does not require the ATT prompt. If your app shows an ATT prompt for other reasons, you can gate analytics on the user's choice:

```swift
ATTrackingManager.requestTrackingAuthorization { status in
    let allowed = status == .authorized
    QuietMetrix.shared.setAnalyticsEnabled(allowed)
}
```