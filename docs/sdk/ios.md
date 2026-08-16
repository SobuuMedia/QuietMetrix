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
            trackingEndpoint: "https://your-server.com/api/v1/track",
            apiKey: "qm_ak_your_api_key",
            flushIntervalMs: 30_000,
            maxQueueSize: 1000
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
    trackingEndpoint: "https://your-server.com/api/v1/track",
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

Events are automatically buffered locally when the device is offline and sent when connectivity is restored. The `was_offline` flag is set on events captured while disconnected. QuietMetrix uses `Reachability` to detect network changes and triggers a flush when the connection is re-established.

## App Tracking Transparency

QuietMetrix does not use IDFA and does not require the ATT prompt. If your app shows an ATT prompt for other reasons, you can gate analytics on the user's choice:

```swift
ATTrackingManager.requestTrackingAuthorization { status in
    let allowed = status == .authorized
    QuietMetrix.shared.setAnalyticsEnabled(allowed)
}
```