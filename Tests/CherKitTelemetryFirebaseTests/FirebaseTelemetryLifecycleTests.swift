import CherKitTelemetry
import XCTest
@testable import CherKitTelemetryFirebase

@MainActor
final class FirebaseTelemetryLifecycleTests: XCTestCase {
    func testInitializationDisablesEveryCollectionFlagAndDeletesPendingReports() async {
        let sdk = RecordingFirebaseTelemetrySDK()
        _ = FirebaseTelemetryBackend(sdk: sdk)
        XCTAssertEqual(sdk.calls.count, 4)
        XCTAssertEqual(Set(sdk.calls), ["crash.false", "performance.false", "instrumentation.false", "delete"])
        XCTAssertFalse(sdk.crashEnabled)
        XCTAssertFalse(sdk.performanceEnabled)
        XCTAssertFalse(sdk.instrumentationEnabled)
        XCTAssertEqual(sdk.deletedReports, 1)
    }

    func testDirectCallsBeforeStartAndAfterStopAreInert() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        for runningPreviously in [false, true] {
            if runningPreviously {
                try backend.start()
                try backend.stop()
            }
            let calls = sdk.calls
            try backend.recordError(TelemetryError(type: "TestError"), attributes: [:])
            try backend.breadcrumb(name: "ready", attributes: [:])
            try backend.setKey("state", value: "ready")
            let id = try backend.startTrace(name: "work", attributes: [:])
            try backend.setAttribute(traceID: id, key: "state", value: "ready")
            try backend.stopTrace(id: id, status: .error)
            XCTAssertEqual(sdk.calls, calls)
        }
    }

    func testStartStopRestartAndDefaultInstrumentationOptOut() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        XCTAssertTrue(sdk.crashEnabled)
        XCTAssertTrue(sdk.performanceEnabled)
        XCTAssertFalse(sdk.instrumentationEnabled)
        XCTAssertFalse(sdk.calls.contains("instrumentation.true"))
        try backend.setKey("state", value: "ready")
        let firstID = try backend.startTrace(name: "work", attributes: [:])
        let firstTrace = try XCTUnwrap(sdk.traces.first)
        try backend.stop()
        XCTAssertEqual(firstTrace.stopAttempts, 1)
        XCTAssertTrue(sdk.keys.isEmpty)
        XCTAssertFalse(sdk.crashEnabled)
        XCTAssertFalse(sdk.performanceEnabled)
        XCTAssertFalse(sdk.instrumentationEnabled)
        XCTAssertEqual(sdk.deletedReports, 2)
        try backend.start()
        try backend.setAttribute(traceID: firstID, key: "state", value: "stale")
        try backend.stopTrace(id: firstID, status: .error)
        XCTAssertEqual(firstTrace.stopAttempts, 1)
        let secondID = try backend.startTrace(name: "work", attributes: [:])
        XCTAssertNotEqual(firstID, secondID)
        try backend.stopTrace(id: secondID, status: .error)
        XCTAssertEqual(sdk.traces.last?.attributes["status"], "error")
        XCTAssertEqual(sdk.traces.last?.stopAttempts, 1)
        try backend.stop()
    }

    func testFacadeDisableStopsAndReenableRestarts() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk, automaticInstrumentationEnabled: true)
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        telemetry.setKey("state", value: "ready")
        let span = telemetry.startSpan(name: "work")
        let trace = try XCTUnwrap(sdk.traces.first)
        telemetry.enabled = false
        XCTAssertFalse(sdk.crashEnabled)
        XCTAssertFalse(sdk.performanceEnabled)
        XCTAssertFalse(sdk.instrumentationEnabled)
        XCTAssertTrue(sdk.keys.isEmpty)
        XCTAssertEqual(trace.stopAttempts, 1)
        telemetry.enabled = true
        XCTAssertTrue(sdk.crashEnabled)
        XCTAssertTrue(sdk.performanceEnabled)
        XCTAssertTrue(sdk.instrumentationEnabled)
        span.end(status: .error)
        span.setAttribute("state", value: "stale")
        XCTAssertEqual(trace.stopAttempts, 1)
        telemetry.startSpan(name: "work").end()
        XCTAssertEqual(sdk.traces.count, 2)
        telemetry.enabled = false
    }

    func testConcurrentSameNameTracesHaveIndependentHandlesAndEndOnce() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        let first = try backend.startTrace(name: "work", attributes: [:])
        let second = try backend.startTrace(name: "work", attributes: [:])
        XCTAssertNotEqual(first, second)
        XCTAssertEqual(sdk.traces.count, 2)
        try backend.stopTrace(id: first, status: .error)
        try backend.stopTrace(id: first, status: .ok)
        try backend.setAttribute(traceID: first, key: "state", value: "late")
        XCTAssertEqual(sdk.traces[0].stopAttempts, 1)
        XCTAssertEqual(sdk.traces[0].attributes["status"], "error")
        XCTAssertEqual(sdk.traces[1].stopAttempts, 0)
        try backend.stop()
        XCTAssertEqual(sdk.traces[1].stopAttempts, 1)
    }
}
