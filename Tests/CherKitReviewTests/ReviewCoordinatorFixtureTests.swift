import CherKitReview
import Foundation
import XCTest

/// Runs every case in `fixtures/review/coordinator.json` (see its description for the step language).
@MainActor
final class ReviewCoordinatorFixtureTests: XCTestCase {
    func testEveryFixtureCase() throws {
        let fixture = try ReviewFixture.load()
        XCTAssertGreaterThanOrEqual(fixture.cases.count, 30)
        for testCase in fixture.cases { try run(testCase) }
    }

    private func run(_ testCase: ReviewFixture.Case) throws {
        let store = InMemoryReviewStore()
        if let stored = testCase.stored {
            store.state = ReviewState(
                successCount: stored.successCount, isDue: stored.isDue, lastPromptedVersion: stored.lastPromptedVersion)
        }
        var clock: TimeInterval = 0
        var eligible = true
        func make(_ version: String) -> ReviewCoordinator {
            ReviewCoordinator(
                store: store,
                appVersion: version,
                enabled: testCase.enabled ?? true,
                successThreshold: testCase.successThreshold ?? ReviewCoordinator.defaultSuccessThreshold,
                cooldown: testCase.cooldownSeconds ?? ReviewCoordinator.defaultCooldown,
                isEligible: { eligible },
                now: { Date(timeIntervalSince1970: clock) }
            )
        }
        var coordinator = make(testCase.version)
        for (index, step) in testCase.steps.enumerated() {
            let at = "\(testCase.name) / step \(index) \(step.do)"
            switch step.do {
            case "recordSuccess": for _ in 0..<(step.times ?? 1) { coordinator.recordSuccess() }
            case "arm": coordinator.arm()
            case "noteInterruption": coordinator.noteInterruption()
            case "interruption":
                coordinator.setInterruptionVisible(try XCTUnwrap(step.visible, at), source: try XCTUnwrap(step.source, at))
            case "eligible": eligible = try XCTUnwrap(step.value, at)
            case "advance": clock += try XCTUnwrap(step.seconds, at)
            case "relaunch": coordinator = make(step.version ?? coordinator.appVersion)
            case "check":
                if let expected = step.shouldPrompt {
                    XCTAssertEqual(coordinator.shouldPrompt(isIdle: step.isIdle ?? true), expected, at)
                }
                if let expected = step.isDue { XCTAssertEqual(coordinator.isDue, expected, at) }
                if let expected = step.cooldownRemaining {
                    XCTAssertEqual(coordinator.cooldownRemaining, expected, accuracy: 0.001, at)
                }
            case "markPrompted": XCTAssertEqual(coordinator.markPrompted(), try XCTUnwrap(step.result, at), at)
            case "expectStored":
                XCTAssertEqual(store.state.successCount, step.successCount, at)
                XCTAssertEqual(store.state.isDue, step.isDue, at)
                XCTAssertEqual(store.state.lastPromptedVersion, step.lastPromptedVersion, at)
            default: XCTFail("unknown step \(at)")
            }
        }
    }
}
