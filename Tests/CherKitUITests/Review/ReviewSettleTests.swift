#if canImport(SwiftUI)
import CherKitReview
@testable import CherKitUI
import Foundation
import XCTest

/// The settle-and-check loop behind `.reviewPrompt`, with a fake sleep that records waits and moves a fake clock.
@MainActor
final class ReviewSettleTests: XCTestCase {
    private var clock = Date(timeIntervalSince1970: 1_000)
    private var sleeps: [TimeInterval] = []

    private func coordinator(due: Bool = true, enabled: Bool = true) -> ReviewCoordinator {
        let store = InMemoryReviewStore(ReviewState(successCount: 0, isDue: due))
        return ReviewCoordinator(
            store: store, appVersion: "2.0", enabled: enabled, cooldown: 60, now: { [unowned self] in self.clock })
    }

    /// Records the wait, advances the clock, and runs `during` once after the `at`th wait (1-based).
    private func fakeSleep(
        maxSleeps: Int = 20, at: Int = 0, during: @escaping () -> Void = {}
    ) -> (TimeInterval) async throws -> Void {
        { [unowned self] seconds in
            sleeps.append(seconds)
            clock = clock.addingTimeInterval(seconds)
            if sleeps.count == at { during() }
            if sleeps.count > maxSleeps { throw CancellationError() }
        }
    }

    func testWaitsTheSettleDelayThenMarksAndRequests() async {
        let review = coordinator()
        var requests = 0
        let shown = await ReviewSettle.awaitThenRequest(coordinator: review, delay: 1.5, sleep: fakeSleep()) {
            XCTAssertFalse(review.isDue, "markPrompted runs right before requestReview")
            requests += 1
        }
        XCTAssertTrue(shown)
        XCTAssertEqual(requests, 1)
        XCTAssertEqual(sleeps, [1.5], "never at once: the settle delay comes first")
        XCTAssertFalse(review.shouldPrompt(), "once per version")
    }

    func testDisabledCoordinatorNeverWaitsOrRequests() async {
        var requests = 0
        let shown = await ReviewSettle.awaitThenRequest(coordinator: .disabled, sleep: fakeSleep()) { requests += 1 }
        XCTAssertFalse(shown)
        XCTAssertEqual(requests, 0)
        XCTAssertEqual(sleeps, [])
    }

    func testNotDueKeepsCheckingEverySettleDelayUntilDue() async {
        let review = coordinator(due: false)
        var requests = 0
        let shown = await ReviewSettle.awaitThenRequest(
            coordinator: review, delay: 2, sleep: fakeSleep(at: 3, during: { review.arm() })
        ) { requests += 1 }
        XCTAssertTrue(shown)
        XCTAssertEqual(requests, 1)
        XCTAssertEqual(sleeps, [2, 2, 2])
    }

    func testVisibleInterruptionHoldsTheRequestThenWaitsCooldownAndSettle() async {
        let review = coordinator()
        review.setInterruptionVisible(true, source: ReviewInterruption.whatsNew)
        var requests = 0
        let shown = await ReviewSettle.awaitThenRequest(
            coordinator: review, delay: 1,
            sleep: fakeSleep(at: 2, during: { review.setInterruptionVisible(false, source: ReviewInterruption.whatsNew) })
        ) { requests += 1 }
        XCTAssertTrue(shown)
        XCTAssertEqual(requests, 1)
        XCTAssertEqual(sleeps, [1, 1, 60, 1], "a cleared modal waits its cooldown, then a fresh settle")
    }

    func testBusyScreenNeverRequests() async {
        let review = coordinator()
        var requests = 0
        let shown = await ReviewSettle.awaitThenRequest(
            coordinator: review, delay: 1, isIdle: { false }, sleep: fakeSleep(maxSleeps: 3)
        ) { requests += 1 }
        XCTAssertFalse(shown, "cancelled while still busy")
        XCTAssertEqual(requests, 0)
        XCTAssertTrue(review.isDue, "nothing consumed")
    }

    func testZeroDelayIsClampedSoItNeverSpins() async {
        let review = coordinator()
        _ = await ReviewSettle.awaitThenRequest(coordinator: review, delay: 0, sleep: fakeSleep()) {}
        XCTAssertEqual(sleeps, [ReviewSettle.minimumDelay])
    }

    func testCancellationStopsWithoutConsuming() async {
        let review = coordinator()
        var requests = 0
        let shown = await ReviewSettle.awaitThenRequest(coordinator: review, sleep: { _ in throw CancellationError() }) {
            requests += 1
        }
        XCTAssertFalse(shown)
        XCTAssertEqual(requests, 0)
        XCTAssertTrue(review.isDue)
    }
}
#endif
