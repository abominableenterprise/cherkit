#if canImport(SwiftUI)
import CherKitReview
@testable import CherKitUI
import XCTest

/// The change tracking behind `.reviewInterruption` and the `onVisibilityChange` / `onPromptChange` callbacks, and the
/// Sunset bridge `setSunsetPromptKind`.
@MainActor
final class ReviewInterruptionTests: XCTestCase {
    func testTrackerReportsEachChangeOnceAndNeverTheInitialHiddenValue() {
        let tracker = VisibilityTracker(hidden: false)
        var reports: [Bool] = []
        tracker.update(false) { reports.append($1) }
        tracker.update(true) { reports.append($1) }
        tracker.update(true) { reports.append($1) }
        tracker.update(false) { reports.append($1) }
        XCTAssertEqual(reports, [true, false])
    }

    func testTrackerPassesTheOldValue() {
        let tracker = VisibilityTracker<SunsetPromptKind?>(hidden: nil)
        var olds: [SunsetPromptKind?] = []
        var news: [SunsetPromptKind?] = []
        for kind in [SunsetPromptKind.soft, .hard, nil] {
            tracker.update(kind) { olds.append($0); news.append($1) }
        }
        XCTAssertEqual(olds, [nil, .soft, .hard])
        XCTAssertEqual(news, [.soft, .hard, nil])
    }

    func testSunsetPromptKindReportsSoftAndHardAsTheirOwnSources() {
        let review = ReviewCoordinator(store: InMemoryReviewStore(ReviewState(isDue: true)), appVersion: "1")
        review.setSunsetPromptKind(.soft)
        XCTAssertEqual(review.visibleInterruptions, [ReviewInterruption.sunsetPrompt])
        review.setSunsetPromptKind(.hard)
        XCTAssertEqual(review.visibleInterruptions, [ReviewInterruption.sunsetBlock])
        review.setSunsetPromptKind(nil)
        XCTAssertEqual(review.visibleInterruptions, [])
        XCTAssertFalse(review.shouldPrompt(), "hiding starts the cooldown")
    }

    func testWhatsNewSourceMatchesKotlin() {
        XCTAssertEqual(ReviewInterruption.whatsNew, "whatsNew")
    }
}
#endif
