import CherKitCore
import CherKitTelemetry
import XCTest

@MainActor
final class TelemetrySinkGateTests: XCTestCase {
    func testCoreSinkConformanceUsesTheNormalGatesAndValidation() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let sink: any TelemetrySink = telemetry
        let attributes: [String: Any] = ["kind": "soft", "message": "private message text"]

        telemetry.enabled = false
        sink.recordError(domain: "cherkit.sunset", code: 1, attributes: attributes)
        sink.breadcrumb(name: "cherkit.sunset.decision", attributes: attributes)
        XCTAssertTrue(backend.errors.isEmpty)
        XCTAssertTrue(backend.breadcrumbs.isEmpty)

        telemetry.enabled = true
        telemetry.restricted = true
        sink.recordError(domain: "cherkit.sunset", code: 2, attributes: attributes)
        sink.breadcrumb(name: "cherkit.sunset.decision", attributes: attributes)
        XCTAssertEqual(backend.errors.map(\.code), [2])
        XCTAssertTrue(backend.breadcrumbs.isEmpty)

        telemetry.restricted = false
        sink.breadcrumb(name: "cherkit.sunset.decision", attributes: attributes)
        sink.breadcrumb(name: "Invalid Name", attributes: attributes)
        XCTAssertEqual(backend.breadcrumbs, ["cherkit.sunset.decision"])
        XCTAssertEqual(backend.attributes.count, 2)
        for recorded in backend.attributes {
            XCTAssertEqual(recorded.count, 1)
            XCTAssertEqual(recorded["kind"] as? String, "soft")
        }
    }

    func testBuildDisabledSinkIsANoOp() {
        let backend = RecordingBackend()
        let sink: any TelemetrySink = Telemetry(backend: backend, buildEnabled: false)
        sink.recordError(domain: "cherkit.sunset", code: 1, attributes: [:])
        sink.breadcrumb(name: "cherkit.sunset.decision", attributes: [:])
        XCTAssertTrue(backend.calls.isEmpty)
    }
}
