import CherKitCore
import CherKitReview
import CherKitTestSupport
import XCTest

@MainActor
final class ReviewTelemetryTests: XCTestCase {
    func testPromptBreadcrumbOnlyOnSuccessfulMarkAndNeverContainsAppData() throws {
        let fixture = try Fixtures.decode(Contract.self, module: "review", name: "telemetry.json")
        for item in fixture.cases {
            let sink = RecordingSink()
            let review = ReviewCoordinator(
                store: InMemoryReviewStore(), appVersion: "private version text",
                enabled: item.enabled, isEligible: { item.eligible }, telemetrySink: sink)
            if item.armed { review.arm() }
            if item.interrupted { review.setInterruptionVisible(true, source: "private modal text") }
            XCTAssertEqual(review.markPrompted(), item.prompted, item.name)
            XCTAssertFalse(review.markPrompted(), item.name)
            XCTAssertEqual(sink.breadcrumbs, item.prompted ? [fixture.breadcrumb] : [], item.name)
            XCTAssertTrue(sink.attributes.allSatisfy(\.isEmpty))
            XCTAssertEqual(sink.errors, 0)
        }
    }

    func testNilSinkDoesNotChangePromptState() {
        let review = ReviewCoordinator(store: InMemoryReviewStore(), appVersion: "1.0", telemetrySink: nil)
        review.arm()
        XCTAssertTrue(review.markPrompted())
        XCTAssertFalse(review.markPrompted())
        XCTAssertFalse(review.isDue)
    }

    private struct Contract: Decodable {
        let breadcrumb: String
        let cases: [Case]
        struct Case: Decodable {
            let name: String
            let enabled: Bool
            let eligible: Bool
            let armed: Bool
            let interrupted: Bool
            let prompted: Bool
        }
    }

    private final class RecordingSink: TelemetrySink {
        var errors = 0
        var breadcrumbs: [String] = []
        var attributes: [[String: Any]] = []
        func recordError(domain: String, code: Int, attributes: [String: Any]) { errors += 1 }
        func breadcrumb(name: String, attributes: [String: Any]) {
            breadcrumbs.append(name)
            self.attributes.append(attributes)
        }
    }
}
