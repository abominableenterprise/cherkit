@testable import CherKitReview
import Foundation
import XCTest

/// `UserDefaultsReviewStore`, `disabled` and the installed-version reader.
@MainActor
final class ReviewStoreTests: XCTestCase {
    func testUserDefaultsStoreRoundTripsUnderTheDefaultPrefix() throws {
        let (defaults, suite) = try freshDefaults()
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = UserDefaultsReviewStore(defaults: defaults)
        XCTAssertEqual(store.load(), ReviewState())

        store.save(ReviewState(successCount: 3, isDue: true, lastPromptedVersion: "1.2"))
        XCTAssertEqual(store.load(), ReviewState(successCount: 3, isDue: true, lastPromptedVersion: "1.2"))
        XCTAssertEqual(defaults.integer(forKey: "cherkit.review.successCount"), 3)
        XCTAssertTrue(defaults.bool(forKey: "cherkit.review.isDue"))
        XCTAssertEqual(defaults.string(forKey: "cherkit.review.lastPromptedVersion"), "1.2")

        store.save(ReviewState(successCount: 0, isDue: false, lastPromptedVersion: nil))
        XCTAssertNil(defaults.object(forKey: "cherkit.review.lastPromptedVersion"))
        XCTAssertEqual(UserDefaultsReviewStore(defaults: defaults, keyPrefix: "other.").load(), ReviewState())
    }

    func testUserDefaultsStoreTreatsWrongTypesAsAbsent() throws {
        let (defaults, suite) = try freshDefaults()
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set("5", forKey: "t.successCount")
        defaults.set("yes", forKey: "t.isDue")
        defaults.set(12, forKey: "t.lastPromptedVersion")
        XCTAssertEqual(UserDefaultsReviewStore(defaults: defaults, keyPrefix: "t.").load(), ReviewState())

        defaults.set(2.5, forKey: "t.successCount")
        defaults.set(7, forKey: "t.isDue")
        defaults.set(["1.0"], forKey: "t.lastPromptedVersion")
        XCTAssertEqual(UserDefaultsReviewStore(defaults: defaults, keyPrefix: "t.").load(), ReviewState())
    }

    func testACoordinatorOverUserDefaultsPersistsAcrossLaunches() throws {
        let (defaults, suite) = try freshDefaults()
        defer { defaults.removePersistentDomain(forName: suite) }
        let first = ReviewCoordinator(store: UserDefaultsReviewStore(defaults: defaults), appVersion: "1.0")
        first.arm()
        let second = ReviewCoordinator(store: UserDefaultsReviewStore(defaults: defaults), appVersion: "1.0")
        XCTAssertTrue(second.markPrompted())
        let third = ReviewCoordinator(store: UserDefaultsReviewStore(defaults: defaults), appVersion: "1.0")
        third.arm()
        XCTAssertFalse(third.isDue)
    }

    func testDisabledIsSwitchedOffAndOrdinaryCoordinatorsAreNot() {
        let disabled = ReviewCoordinator.disabled
        XCTAssertTrue(disabled.isDisabled)
        disabled.arm()
        disabled.setInterruptionVisible(true, source: ReviewInterruption.sunsetBlock)
        XCTAssertFalse(disabled.isDue)
        XCTAssertFalse(disabled.shouldPrompt())
        XCTAssertTrue(disabled.visibleInterruptions.isEmpty)
        XCTAssertFalse(ReviewCoordinator(store: InMemoryReviewStore(), appVersion: "1.0").isDisabled)
        XCTAssertTrue(ReviewCoordinator(store: InMemoryReviewStore(), appVersion: "1.0", enabled: false).isDisabled)
    }

    func testEligibilityIsReadOnEveryCall() {
        let review = ReviewCoordinator(store: InMemoryReviewStore(), appVersion: "1.0")
        review.arm()
        review.isEligible = { false }
        XCTAssertFalse(review.shouldPrompt())
        review.isEligible = { true }
        XCTAssertTrue(review.shouldPrompt())
    }

    func testInstalledVersionIsEmptyForABundleWithoutOne() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("Bare-\(UUID().uuidString).bundle")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        XCTAssertEqual(ReviewCoordinator.installedVersion(bundle: try XCTUnwrap(Bundle(path: dir.path))), "")
    }

    private func freshDefaults() throws -> (UserDefaults, String) {
        let suite = "cherkit.review.tests.\(UUID().uuidString)"
        return (try XCTUnwrap(UserDefaults(suiteName: suite)), suite)
    }
}
