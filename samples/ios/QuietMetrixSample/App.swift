import SwiftUI
import QuietMetrix

@main
struct QuietMetrixApp: App {
    init() {
        let config = QuietMetrixConfig(
            storageKeyPrefix: "qmios_",
            trackingEndpoint: "https://your-server.com/api/v1/track",
            apiKey: "qm_ak_local_demo_key",
            flushIntervalMs: 30_000,
            autoTrackInitialPageView: false,
            trackingAllowedByDefault: false,
            userAgent: nil,
            applicationContext: nil,
            debug: true,
            funnels: [],
            funnelManifest: nil,
            collectAnonymousId: false,
            activationEvent: nil,
            activationWindowDays: 3,
            countryCode: "FR",
            languageTag: "fr"
        )
        QuietMetrix.shared.doInit(config: config)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
