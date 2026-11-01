#if canImport(SwiftUI)
import CherKitReview
import SwiftUI

@available(iOS 16, macOS 13, *)
extension View {
    /// Reports one of your sheets, alerts or banners to `coordinator` as an interruption: while `isPresented` is true
    /// the review never shows; when it turns false (or this view goes away) the coordinator's cooldown starts, so the
    /// review waits for a fresh settle instead of firing at once. Attach it next to the presentation:
    ///
    /// ```swift
    /// .sheet(isPresented: $showPaywall) { PaywallView() }
    /// .reviewInterruption(isPresented: showPaywall, source: "paywall", coordinator: review)
    /// ```
    ///
    /// Give each presentation its own `source`: two with the same string share one entry, and the first to hide
    /// clears it. ``ReviewInterruption`` has the well-known ones.
    public func reviewInterruption(
        isPresented: Bool,
        source: String,
        coordinator: ReviewCoordinator
    ) -> some View {
        modifier(VisibilityChangeReporter(value: isPresented, hidden: false) { _, visible in
            coordinator.setInterruptionVisible(visible, source: source)
        })
    }
}
#endif
