import CherKitAnalytics
import CherKitTestSupport
import XCTest

@MainActor
final class AnalyticsGateTests: XCTestCase {
    func testEveryGateFixtureCase() throws {
        let fixture = try Fixtures.decode(GateFixture.self, module: "analytics", name: "gate.json")
        XCTAssertGreaterThanOrEqual(fixture.cases.count, 8)
        for testCase in fixture.cases {
            let backend = RecordingBackend()
            let analytics = Analytics<NamedEvent>(backend: backend, buildEnabled: testCase.buildEnabled)
            analytics.consentGranted = testCase.consentGranted
            analytics.sessionActive = testCase.sessionActive

            let sent = analytics.track(NamedEvent(name: testCase.event))

            XCTAssertEqual(sent, testCase.sent, testCase.description)
            XCTAssertEqual(backend.calls.contains("capture:\(testCase.event)"), testCase.sent, testCase.description)
            XCTAssertEqual(AnalyticsEventName.isValid(testCase.event) && analytics.isGateOpen, testCase.sent,
                           testCase.description)
        }
    }
}
