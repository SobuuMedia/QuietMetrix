import SwiftUI
import QuietMetrix

@MainActor
private final class QuietMetrixExperimentModel: ObservableObject {
    @Published private(set) var variant: String?
    private var revision: Int = 0
    private var hasPinnedDecision = false
    private var subscription: ExperimentSubscription?

    init(key: String) {
        subscription = ExperimentDecisionsKt.observeExperimentState(key: key) { [weak self] snapshot in
            DispatchQueue.main.async {
                guard let self else { return }
                if snapshot.state != "ready" && snapshot.state != "pending" {
                    self.variant = nil
                    self.revision = 0
                    self.hasPinnedDecision = false
                    return
                }
                guard !self.hasPinnedDecision, snapshot.state == "ready" else { return }
                self.variant = snapshot.variant
                self.revision = Int(snapshot.revision)
                self.hasPinnedDecision = true
            }
        }
    }

    func recordExposure(key: String) {
        guard let variant else { return }
        ExperimentDecisionsKt.recordExperimentExposureForSnapshot(key: key, revision: Int32(revision), variantId: variant)
    }

    deinit {
        subscription?.close()
    }
}

/// Shows the tested element for B. A, Pending and unavailable states render `control`.
public struct ExperimentElement<Control: View, Content: View>: View {
    private let key: String
    private let control: Control
    private let content: Content
    @StateObject private var model: QuietMetrixExperimentModel

    public init(
        key: String,
        @ViewBuilder control: () -> Control,
        @ViewBuilder content: () -> Content
    ) {
        self.key = key
        self.control = control()
        self.content = content()
        _model = StateObject(wrappedValue: QuietMetrixExperimentModel(key: key))
    }

    public var body: some View {
        Group {
            if model.variant == "b" { content } else { control }
        }
        .onAppear { model.recordExposure(key: key) }
        .onChange(of: model.variant) { _, variant in
            if variant != nil { model.recordExposure(key: key) }
        }
    }
}

/// Compares two app-authored views; A is the fallback until a Ready decision arrives.
public struct ExperimentVariants<VariantA: View, VariantB: View>: View {
    private let key: String
    private let variantA: VariantA
    private let variantB: VariantB
    @StateObject private var model: QuietMetrixExperimentModel

    public init(
        key: String,
        @ViewBuilder variantA: () -> VariantA,
        @ViewBuilder variantB: () -> VariantB
    ) {
        self.key = key
        self.variantA = variantA()
        self.variantB = variantB()
        _model = StateObject(wrappedValue: QuietMetrixExperimentModel(key: key))
    }

    public var body: some View {
        Group {
            if model.variant == "b" { variantB } else { variantA }
        }
        .onAppear { model.recordExposure(key: key) }
        .onChange(of: model.variant) { _, variant in
            if variant != nil { model.recordExposure(key: key) }
        }
    }
}
