#if canImport(UIKit) && !os(watchOS)
import UIKit

/// Shows a view controller in its own window above every other window of a scene, so it covers the sheets, covers
/// and alerts the app may already be presenting. The hard block uses it on iOS: a `fullScreenCover` cannot be
/// presented from a view that is already presenting something, so it would silently not appear.
@MainActor
final class SunsetBlockingWindow {
    /// Above alerts, so nothing the app presents later covers the block either.
    static let level = UIWindow.Level.alert + 1

    /// The window while the block is shown.
    private(set) var window: UIWindow?

    /// Shows `controller` above everything in `scene`. Calling it again with the same controller and scene does nothing.
    func show(_ controller: UIViewController, in scene: UIWindowScene) {
        if let window, window.windowScene === scene, window.rootViewController === controller { return }
        hide()
        let window = UIWindow(windowScene: scene)
        window.windowLevel = Self.level
        window.rootViewController = controller
        // VoiceOver ignores the app's windows underneath.
        controller.view.accessibilityViewIsModal = true
        window.makeKeyAndVisible()
        self.window = window
    }

    /// Removes the block.
    func hide() {
        window?.isHidden = true
        window = nil
    }
}
#endif
