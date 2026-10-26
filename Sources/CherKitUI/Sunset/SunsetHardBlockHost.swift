#if canImport(SwiftUI) && canImport(UIKit) && !os(watchOS)
import CherKitSunset
import SwiftUI
import UIKit

/// iOS hard block: while `prompt` is set, shows ``SunsetUpdateView`` in a ``SunsetBlockingWindow`` above every window
/// of the scene this view lives in, so it takes over even when the app already presents a sheet, cover or alert.
@available(iOS 16, *)
struct SunsetHardBlockHost: UIViewRepresentable {
    let prompt: SunsetPrompt?
    let strings: SunsetStrings
    let appIcon: Image?

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> SceneTrackingView {
        let view = SceneTrackingView()
        view.isUserInteractionEnabled = false
        view.onWindowChange = { [weak coordinator = context.coordinator] in coordinator?.update() }
        return view
    }

    func updateUIView(_ view: SceneTrackingView, context: Context) {
        let coordinator = context.coordinator
        coordinator.view = view
        coordinator.block = prompt.map { SunsetUpdateView(prompt: $0, strings: strings, appIcon: appIcon) }
        coordinator.update()
    }

    static func dismantleUIView(_ view: SceneTrackingView, coordinator: Coordinator) {
        coordinator.blockingWindow.hide()
    }

    @MainActor
    final class Coordinator {
        let blockingWindow = SunsetBlockingWindow()
        weak var view: SceneTrackingView?
        var block: SunsetUpdateView?
        private var host: UIHostingController<SunsetUpdateView>?

        func update() {
            guard let block, let scene = view?.window?.windowScene else {
                // No scene yet: shown once this view is in a window (`onWindowChange`).
                if block == nil { blockingWindow.hide(); host = nil }
                return
            }
            if let host { host.rootView = block } else { host = UIHostingController(rootView: block) }
            blockingWindow.show(host!, in: scene)
        }
    }

    /// Reports when it moves into a window, so the block can find its scene.
    final class SceneTrackingView: UIView {
        var onWindowChange: (() -> Void)?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            onWindowChange?()
        }
    }
}
#endif
