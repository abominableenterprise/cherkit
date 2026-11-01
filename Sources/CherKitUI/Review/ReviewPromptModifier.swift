#if canImport(SwiftUI) && canImport(StoreKit) && (os(iOS) || os(macOS))
import CherKitReview
import StoreKit
import SwiftUI

/// Backs ``SwiftUI/View/reviewPrompt(coordinator:isIdle:settleDelay:)``: runs ``ReviewSettle`` while the scene is
/// active and the screen idle, and asks StoreKit's `requestReview` once it says so.
@available(iOS 16, macOS 13, *)
struct ReviewPromptModifier: ViewModifier {
    let coordinator: ReviewCoordinator
    let isIdle: Bool
    let settleDelay: TimeInterval

    @Environment(\.requestReview) private var requestReview
    @Environment(\.scenePhase) private var scenePhase

    /// What a settle run depends on: any change cancels the running one and starts over.
    struct Run: Equatable {
        let coordinator: ObjectIdentifier
        let isActive: Bool
        let isIdle: Bool
        let settleDelay: TimeInterval

        /// Whether a settle run may start at all.
        var mayPrompt: Bool { isActive && isIdle }
    }

    var run: Run {
        Run(
            coordinator: ObjectIdentifier(coordinator),
            isActive: scenePhase == .active,
            isIdle: isIdle,
            settleDelay: settleDelay
        )
    }

    func body(content: Content) -> some View {
        let run = run
        content.task(id: run) {
            guard run.mayPrompt else { return }
            _ = await ReviewSettle.awaitThenRequest(coordinator: coordinator, delay: run.settleDelay) {
                requestReview()
            }
        }
    }
}

@available(iOS 16, macOS 13, *)
extension View {
    /// Asks for an App Store review when `coordinator` says it is due, once the screen has settled. Attach it to your
    /// main screens (not to a sheet), and report your own sheets and alerts with
    /// ``reviewInterruption(isPresented:source:coordinator:)``:
    ///
    /// ```swift
    /// HomeView().reviewPrompt(coordinator: review, isIdle: !isEditing)
    /// ```
    ///
    /// - Runs only while this view is on screen, its scene is `.active` and `isIdle` is true. It waits `settleDelay`
    ///   first, then checks `shouldPrompt(isIdle:)`, and checks again every `settleDelay` (after any interruption
    ///   cooldown) while the screen stays idle, so a prompt that becomes due on this screen still shows.
    /// - Starts over whenever the scene becomes active again and whenever `isIdle` turns true: leaving the app, a
    ///   system alert or a busy screen cancels the wait.
    /// - Calls `markPrompted()` right before StoreKit's `requestReview`. The version counts as prompted even if
    ///   StoreKit's quota shows nothing: StoreKit gives no signal either way. `requestReview` never throws, so a
    ///   failure stays quiet.
    /// - ``ReviewCoordinator/disabled`` (or `enabled: false`) does nothing: pass it in debug builds and test hosts.
    ///
    /// - Parameters:
    ///   - coordinator: The app's coordinator, kept for the app's lifetime; ``ReviewCoordinator/disabled`` in tests.
    ///   - isIdle: `false` while the screen is busy (editing, a running task); the review then waits.
    ///   - settleDelay: Seconds the screen must stay idle first; 1.5 by default, at least 0.1.
    public func reviewPrompt(
        coordinator: ReviewCoordinator,
        isIdle: Bool = true,
        settleDelay: TimeInterval = 1.5
    ) -> some View {
        modifier(ReviewPromptModifier(coordinator: coordinator, isIdle: isIdle, settleDelay: settleDelay))
    }
}
#endif
