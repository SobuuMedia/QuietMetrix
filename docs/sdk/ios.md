# SDK: iOS

> Setting this up for the first time? An AI agent can create the project and fill in the
> values below for you — see [Agent-driven setup](../agents/setup.md).

## Installation

### XCFramework and local Swift Package Manager

Build the framework from this repository:

```sh
./gradlew :quietmetrix-sdk:assembleQuietMetrixXCFramework
```

The release artifact is at
`quietmetrix-sdk/build/XCFrameworks/release/QuietMetrix.xcframework`.
Add it to Xcode's Frameworks, Libraries, and Embedded Content. This static framework
should use **Do Not Embed**. The [iOS sample](../../samples/ios/QuietMetrixSample/README.md)
includes a working local Swift package and SwiftUI experiment wrappers. Follow its copy
and type-check commands. A remote Swift package is not currently published; do not use
an unverified package URL.

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
            autoTrackInitialPageView: false,
            trackingAllowedByDefault: false,
            userAgent: nil,
            applicationContext: nil,
            debug: false,
            funnels: [],
            funnelManifest: nil,
            collectAnonymousId: false,
            activationEvent: nil,
            activationWindowDays: 3,
            countryCode: nil,
            languageTag: nil
        )
        QuietMetrix.shared.doInit(config: config)
        return true
    }
}
```

## Track Events

```swift
EventsKt.trackEvent(event: "button_click", screen: "settings", props: [:]) { _ in }
EventsKt.trackEvent(event: "page_view", screen: "home", props: ["tab": "featured"]) { _ in }
```

### Objective-C

```objc
[QuietMetrixEventsKt trackEventEvent:@"button_click" screen:@"settings" props:@{} completionHandler:^(NSError *error) {}];
[QuietMetrixEventsKt trackEventEvent:@"page_view" screen:@"home" props:@{@"tab": @"featured"} completionHandler:^(NSError *error) {}];
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
        FunnelStep(key: "view", event: "screen_view", name: nil, screen: "signup", props: [:]),
        FunnelStep(key: "submit", event: "signup_submitted", name: nil, screen: nil, props: [:]),
    ],
    windowSeconds: 86_400,
    description: nil,
    countMode: .actor,
    identityScope: .installOrSession,
    correlationProperty: nil
)

let config = QuietMetrixConfig(
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1",
    apiKey: "qm_ak_your_api_key",
    flushIntervalMs: 30_000,
    autoTrackInitialPageView: false,
    trackingAllowedByDefault: false,
    userAgent: nil,
    applicationContext: nil,
    debug: false,
    funnels: [signupFunnel],
    funnelManifest: nil,
    collectAnonymousId: false,
    activationEvent: nil,
    activationWindowDays: 3,
    countryCode: nil,
    languageTag: nil
)
QuietMetrix.shared.doInit(config: config)
```

## Experiments (A/B testing)

Create an experiment in the dashboard's Experiments tab, then branch on it in code — no
config to declare here, unlike funnels. See the [Experiments guide](experiments.md) for the
full concept, timing, and country-targeting details. The functions are exposed as top-level
Kotlin functions, so Swift/Objective-C sees them namespaced on `ExperimentsKt`:

```swift
switch ExperimentsKt.getVariant(experimentKey: "checkout_cta") {
case "b":
    NewCheckoutButton { ExperimentsKt.trackExperimentInteraction(experimentKey: "checkout_cta") }
default:
    OldCheckoutButton() // "a", or "none" if not enrolled
}
```

## Consent

```swift
// After user accepts cookie consent
CookieConsentKt.setCookieConsent(accepted: true)

// Master kill switch — separate from cookie consent
QuietMetrix.shared.setAnalyticsEnabled(false)

// Check consent state
if CookieConsent_iosKt.isTrackingAllowed() {
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

Pending counters and immutable retry batches use NSUserDefaults-backed persistence.
A failed request retains the same receipt for retry. Visits are recorded at foreground
start; completed duration is recorded on background. Unfinished duration after an OS kill
is omitted. Consent and analytics opt-out persist across launches.

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
