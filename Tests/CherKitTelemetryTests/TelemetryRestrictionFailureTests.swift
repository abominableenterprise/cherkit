import CherKitTelemetry
import XCTest

@MainActor
final class TelemetryRestrictionFailureTests: XCTestCase {
    func testFailedInitialUpdateMustSucceedBeforeLazyStart() {
        let backend = RecordingBackend()
        backend.failNext = ["update"]
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        XCTAssertNil(backend.appliedRestriction)
        XCTAssertTrue(backend.calls.isEmpty)

        backend.failNext = ["update"]
        telemetry.recordError(domain: "test", code: 1)
        XCTAssertTrue(backend.startRestrictions.isEmpty)
        XCTAssertTrue(backend.errors.isEmpty)

        telemetry.recordError(domain: "test", code: 2)
        XCTAssertEqual(backend.restrictionUpdates, [false, false, false])
        XCTAssertEqual(backend.startRestrictions, [false])
        XCTAssertEqual(backend.errors.map(\.code), [2])
    }

    func testActiveUpdateFailureStopsAndRetriesBeforeRecordingRestrictedErrors() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let span = telemetry.startSpan(name: "work")
        backend.calls.removeAll()
        backend.failNext = ["update", "stop"]

        telemetry.restricted = true
        XCTAssertEqual(backend.calls, ["stopTrace", "stop"])
        XCTAssertTrue(backend.liveTraces.isEmpty)
        XCTAssertEqual(backend.appliedRestriction, false)

        backend.failNext = ["update"]
        telemetry.recordError(domain: "test", code: 1)
        XCTAssertTrue(backend.errors.isEmpty)
        XCTAssertEqual(backend.startRestrictions, [false])

        telemetry.recordError(domain: "test", code: 2)
        XCTAssertEqual(backend.startRestrictions, [false, true])
        XCTAssertEqual(backend.errors.map(\.code), [2])
        let count = backend.calls.count
        telemetry.breadcrumb(name: "ready")
        telemetry.setKey("state", value: "ready")
        telemetry.startSpan(name: "blocked").end()
        span.setAttribute("state", value: "ready")
        span.end()
        XCTAssertEqual(backend.calls.count, count)
    }

    func testEnabledRestartRequiresFreshSuccessfulRestrictionUpdate() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        telemetry.recordError(domain: "test", code: 1)
        telemetry.enabled = false
        backend.calls.removeAll()
        backend.restrictionUpdates.removeAll()
        backend.failNext = ["update"]

        telemetry.enabled = true
        XCTAssertEqual(backend.restrictionUpdates, [false])
        XCTAssertEqual(backend.startRestrictions, [false])
        XCTAssertEqual(backend.calls, ["stop"])

        telemetry.enabled = true
        XCTAssertEqual(backend.restrictionUpdates, [false, false])
        XCTAssertEqual(backend.startRestrictions, [false, false])
        XCTAssertEqual(backend.calls, ["stop", "start"])
    }

    func testFailedUnchangedRestrictionInvalidatesSpansAcrossRestart() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let span = telemetry.startSpan(name: "work")
        backend.failNext = ["update"]

        telemetry.restricted = false
        XCTAssertTrue(backend.liveTraces.isEmpty)
        XCTAssertEqual(backend.calls.suffix(2), ["stopTrace", "stop"])
        telemetry.recordError(domain: "test", code: 1)
        let count = backend.calls.count
        span.setAttribute("state", value: "ready")
        span.end()
        XCTAssertEqual(backend.calls.count, count)
        XCTAssertTrue(backend.traceAttributes.isEmpty)
    }
}
