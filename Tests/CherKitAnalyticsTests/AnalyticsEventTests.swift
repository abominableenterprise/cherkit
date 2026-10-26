import CherKitAnalytics
import XCTest

@MainActor
final class AnalyticsEventTests: XCTestCase {
    private enum AppEvent: String, AnalyticsEvent, CaseIterable {
        case appOpened = "app_opened"
        case itemAdded = "item_added"
    }

    func testStringEnumUsesRawValueAsName() {
        XCTAssertEqual(AppEvent.appOpened.name, "app_opened")
    }

    func testAppEnumIsTrackedByName() {
        let backend = RecordingBackend()
        let analytics = Analytics<AppEvent>(backend: backend, buildEnabled: true)
        analytics.consentGranted = true
        analytics.sessionActive = true

        XCTAssertTrue(analytics.track(.itemAdded))
        XCTAssertEqual(backend.calls, ["start", "capture:item_added"])
    }

    func testAppsCanCheckTheirWholeAllowlist() {
        XCTAssertTrue(AppEvent.allCases.allSatisfy { AnalyticsEventName.isValid($0.name) })
    }

    func testConsentAndSessionDefaultToFalse() {
        let analytics = Analytics<AppEvent>(backend: RecordingBackend(), buildEnabled: true)
        XCTAssertFalse(analytics.consentGranted)
        XCTAssertFalse(analytics.sessionActive)
        XCTAssertFalse(analytics.isGateOpen)
    }
}
