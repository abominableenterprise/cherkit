#if canImport(SwiftUI)
import CherKitReview
import Foundation

/// The settle-and-check loop behind ``SwiftUI/View/reviewPrompt(coordinator:isIdle:settleDelay:)``, free of view code
/// so it is tested with a fake sleep. The Swift twin of Android's `ReviewSettle.awaitThenLaunch`.
@MainActor
enum ReviewSettle {
    /// How long the screen must stay idle before the review may show: 1.5 seconds.
    nonisolated static let defaultDelay: TimeInterval = 1.5
    /// Keeps a zero delay from spinning.
    nonisolated static let minimumDelay: TimeInterval = 0.1

    /// Waits until the review may show, then calls `request` and returns `true`. Run it only while the screen is idle
    /// and in front; cancel it when either stops being true.
    ///
    /// - Waits `delay` first, so the review never shows the moment a screen appears.
    /// - Then asks `shouldPrompt(isIdle:) && markPrompted()`; while that is false, asks again every `delay`. While an
    ///   interruption's cooldown runs it waits the cooldown out, then `delay` again, so a cleared modal never fires
    ///   the review at once.
    /// - `markPrompted()` runs right before `request`: the version counts as prompted even if StoreKit shows nothing.
    /// - Returns `false` without waiting for a disabled coordinator, and `false` once cancelled (`sleep` throws).
    static func awaitThenRequest(
        coordinator: ReviewCoordinator,
        delay: TimeInterval = defaultDelay,
        isIdle: () -> Bool = { true },
        sleep: (TimeInterval) async throws -> Void = ReviewSettle.sleep,
        request: () -> Void
    ) async -> Bool {
        guard !coordinator.isDisabled else { return false }
        let step = max(minimumDelay, delay)
        do {
            while true {
                try await sleep(step)
                if coordinator.shouldPrompt(isIdle: isIdle()) && coordinator.markPrompted() {
                    request()
                    return true
                }
                let cooldown = coordinator.cooldownRemaining
                if cooldown > 0 { try await sleep(cooldown) }
            }
        } catch {
            return false
        }
    }

    /// `Task.sleep`, which throws once the task is cancelled.
    nonisolated static func sleep(_ seconds: TimeInterval) async throws {
        try await Task.sleep(nanoseconds: UInt64(max(0, seconds) * 1_000_000_000))
    }
}
#endif
