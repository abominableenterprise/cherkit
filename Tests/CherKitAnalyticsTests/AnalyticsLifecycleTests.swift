import CherKitAnalytics
import CherKitTestSupport
import XCTest

@MainActor
final class AnalyticsLifecycleTests: XCTestCase {
    func testEveryLifecycleFixtureCase() throws {
        let fixture = try Fixtures.decode(LifecycleFixture.self, module: "analytics", name: "lifecycle.json")
        XCTAssertFalse(fixture.cases.isEmpty)
        for testCase in fixture.cases {
            let backend = RecordingBackend()
            let analytics = Analytics<NamedEvent>(backend: backend, buildEnabled: testCase.buildEnabled)
            for (index, operation) in testCase.operations.enumerated() {
                let label = "\(testCase.description) [op \(index): \(operation.op)]"
                try apply(operation, to: analytics, backend: backend, label: label)
            }
            XCTAssertEqual(backend.calls, testCase.calls, testCase.description)
        }
    }

    private func apply(
        _ operation: LifecycleFixture.Operation,
        to analytics: Analytics<NamedEvent>,
        backend: RecordingBackend,
        label: String
    ) throws {
        switch operation.op {
        case "setConsent":
            analytics.consentGranted = try XCTUnwrap(operation.value, label)
        case "setSession":
            analytics.sessionActive = try XCTUnwrap(operation.value, label)
        case "track":
            let sent = analytics.track(NamedEvent(name: try XCTUnwrap(operation.event, label)))
            XCTAssertEqual(sent, try XCTUnwrap(operation.sent, label), label)
        case "failNext":
            backend.failNext.insert(try XCTUnwrap(operation.call, label))
        default:
            XCTFail("unknown operation: \(label)")
        }
    }
}
