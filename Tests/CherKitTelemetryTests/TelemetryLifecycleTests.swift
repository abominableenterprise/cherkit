import CherKitTelemetry
import XCTest

@MainActor
final class TelemetryLifecycleTests: XCTestCase {
    func testLazyStartRollbackRetryAndRestart() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        XCTAssertTrue(telemetry.enabled)
        XCTAssertFalse(telemetry.restricted)
        XCTAssertFalse(telemetry.isDisabled)
        XCTAssertTrue(backend.calls.isEmpty)
        backend.failNext = ["start", "stop"]
        telemetry.recordError(domain: "test", code: 1)
        XCTAssertEqual(backend.calls, ["start", "stop"])
        telemetry.recordError(domain: "test", code: 2)
        XCTAssertEqual(backend.calls, ["start", "stop", "start", "error"])
        backend.failNext.insert("stop")
        telemetry.enabled = false
        telemetry.recordError(domain: "test", code: 3)
        telemetry.enabled = true
        telemetry.recordError(domain: "test", code: 4)
        XCTAssertEqual(backend.calls, ["start", "stop", "start", "error", "stop", "start", "error"])
        XCTAssertEqual(backend.errors.map(\.code), [2, 4])
    }

    func testEnabledAssignmentRetriesFailedStart() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        backend.failNext.insert("start")
        telemetry.enabled = true
        telemetry.enabled = true
        telemetry.enabled = true
        XCTAssertEqual(backend.calls, ["start", "stop", "start"])
    }

    func testAllBackendOperationFailuresAreSwallowed() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        backend.failNext = ["error", "breadcrumb", "key", "startTrace"]
        telemetry.recordError(domain: "test", code: 1)
        telemetry.breadcrumb(name: "ready")
        telemetry.setKey("state", value: "ready")
        let failed = telemetry.startSpan(name: "work")
        failed.setAttribute("state", value: "ready")
        failed.end()
        XCTAssertEqual(backend.calls, ["start", "error", "breadcrumb", "key", "startTrace"])
        let span = telemetry.startSpan(name: "work")
        backend.failNext = ["setAttribute", "stopTrace"]
        span.setAttribute("state", value: "ready")
        span.end(status: .error)
        span.end()
        span.setAttribute("state", value: "ready")
        XCTAssertEqual(backend.calls.suffix(3), ["startTrace", "setAttribute", "stopTrace"])
        XCTAssertEqual(backend.stops.count, 1)
        telemetry.breadcrumb(name: "ready")
        XCTAssertEqual(backend.breadcrumbs, ["ready"])
    }

    func testDisabledInstancesAreFreshAndCannotBeEnabled() {
        let first = Telemetry.disabled
        let second = Telemetry.disabled
        XCTAssertFalse(first === second)
        first.enabled = false
        first.restricted = true
        XCTAssertTrue(second.enabled)
        XCTAssertFalse(second.restricted)
        first.enabled = true
        XCTAssertTrue(first.isDisabled)
        XCTAssertTrue(second.isDisabled)
        XCTAssertFalse(first.buildEnabled)
        first.recordError(domain: "test", code: 1)
        first.breadcrumb(name: "ready")
        first.setKey("state", value: "ready")
        first.startSpan(name: "work").end()
        XCTAssertEqual(first.span("work") { 7 }, 7)
    }

    func testRestrictionDoesNotStartBackendAndBlocksKeys() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        telemetry.restricted = true
        telemetry.breadcrumb(name: "ready")
        telemetry.setKey("state", value: "ready")
        telemetry.startSpan(name: "work").end()
        XCTAssertTrue(backend.calls.isEmpty)
        telemetry.recordError(domain: "test", code: 1, attributes: ["state": "ready"])
        XCTAssertEqual(backend.calls, ["start", "error"])
        XCTAssertEqual(backend.attributes.last?["state"] as? String, "ready")
    }
}
