import SwiftUI
import QuietMetrix

struct ContentView: View {
    @State private var consentAccepted = false
    @State private var email = ""
    @State private var password = ""

    var body: some View {
        VStack(spacing: 20) {
            Text("Sign in")
                .font(.largeTitle.bold())

            ExperimentElement(
                key: "login_help",
                control: { EmptyView() },
                content: {
                    VStack(spacing: 8) {
                        Text("Need help signing in?").font(.headline)
                        Button("Get help") {}
                    }
                }
            )

            ExperimentVariants(
                key: "login_layout",
                variantA: { TextField("Email", text: $email) },
                variantB: {
                    VStack(spacing: 8) {
                        TextField("Email address", text: $email)
                        Text("A refreshed sign-in layout")
                            .font(.caption)
                    }
                }
            )

            SecureField("Password", text: $password)
                .textFieldStyle(.roundedBorder)

            Button("Continue") {
                EventsKt.trackEvent(event: "login_success", screen: "login", props: [:]) { _ in }
            }
            .buttonStyle(.borderedProminent)

            Button(consentAccepted ? "Analytics consent accepted" : "Accept anonymous analytics") {
                consentAccepted = true
                CookieConsentKt.setCookieConsent(accepted: true)
            }
            .font(.footnote)
        }
        .textFieldStyle(.roundedBorder)
        .padding(32)
        .frame(maxWidth: 420)
        .task {
            try? await EventsKt.trackEvent(event: "page_view", screen: "login", props: [:])
        }
    }
}
