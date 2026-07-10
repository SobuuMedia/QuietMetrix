import SwiftUI
import QuietMetrix

@main
struct QuietMetrixApp: App {
    init() {
        let config = QuietMetrixConfig(
            storageKeyPrefix: "qmios_",
            autoTrackInitialPageView: false
        )
        QuietMetrix.shared.initialize(config: config)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
