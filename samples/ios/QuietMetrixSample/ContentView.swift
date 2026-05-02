import SwiftUI
import QuietMetrix

struct ContentView: View {
    var body: some View {
        VStack {
            Text("Hello QuietMetrix")
                .font(.title)
                .onAppear {
                    trackEvent(event: "page_view", screen: "home")
                }
        }
    }
}
