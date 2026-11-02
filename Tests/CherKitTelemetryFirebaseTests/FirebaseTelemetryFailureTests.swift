import CherKitTelemetry
import XCTest
@testable import CherKitTelemetryFirebase

@MainActor
final class FirebaseTelemetryFailureTests: XCTestCase {
    func testInitializationAttemptsEveryPrivacyOperationEvenWhenTheyAllThrow() async {
        let sdk = RecordingFirebaseTelemetrySDK()
        sdk.failures = ["crash.false", "performance.false", "instrumentation.false"]
        _ = FirebaseTelemetryBackend(sdk: sdk)
        XCTAssertEqual(sdk.calls.count, 3)
        XCTAssertEqual(Set(sdk.calls), sdk.failures)
    }

    func testEveryCollectionEnableFailureThrowsAndRollsBackThenAllowsRetry() async throws {
        for failure in ["instrumentation.true", "performance.true", "crash.false", "send"] {
            let sdk = RecordingFirebaseTelemetrySDK()
            let backend = FirebaseTelemetryBackend(sdk: sdk, automaticInstrumentationEnabled: true)
            sdk.failures = [failure]
            XCTAssertThrowsError(try backend.start(), failure)
            XCTAssertFalse(sdk.crashEnabled, failure)
            XCTAssertFalse(sdk.performanceEnabled, failure)
            XCTAssertFalse(sdk.instrumentationEnabled, failure)
            XCTAssertEqual(sdk.deletedReports, 1, failure)
            let rollbackCalls = Set(sdk.calls.suffix(4))
            XCTAssertEqual(rollbackCalls, ["crash.false", "performance.false", "instrumentation.false", "delete"])
            let calls = sdk.calls
            try backend.recordError(TelemetryError(type: "TestError"), attributes: [:])
            try backend.breadcrumb(name: "ready", attributes: [:])
            try backend.setKey("state", value: "ready")
            _ = try backend.startTrace(name: "work", attributes: [:])
            XCTAssertEqual(sdk.calls, calls)
            sdk.failures = []
            try backend.start()
            XCTAssertFalse(sdk.crashEnabled)
            XCTAssertTrue(sdk.performanceEnabled)
            XCTAssertTrue(sdk.instrumentationEnabled)
            try backend.recordError(TelemetryError(type: "TestError"), attributes: [:])
            XCTAssertEqual(sdk.errors.count, 1)
            try backend.stop()
        }
    }

    func testFailedStartAttemptsAllRollbackOperationsEvenIfRollbackAlsoThrows() async {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk, automaticInstrumentationEnabled: true)
        sdk.calls = []
        sdk.failures = ["send", "crash.false", "performance.false", "instrumentation.false", "delete"]
        XCTAssertThrowsError(try backend.start())
        XCTAssertEqual(Set(sdk.calls.suffix(4)), ["crash.false", "performance.false", "instrumentation.false", "delete"])
    }

    func testFacadeSwallowsFailedStartAndRetriesOnNextError() async {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        sdk.failures = ["send"]
        telemetry.recordError(domain: "test.domain", code: 1)
        XCTAssertTrue(sdk.errors.isEmpty)
        XCTAssertFalse(sdk.crashEnabled)
        XCTAssertFalse(sdk.performanceEnabled)
        sdk.failures = []
        telemetry.recordError(domain: "test.domain", code: 2)
        XCTAssertEqual(sdk.errors.map(\.code), [2])
        telemetry.enabled = false
    }

    func testRecordLogKeyAndTraceStartErrorsAreSwallowedByBackend() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        sdk.failures = ["record", "log", "setKey", "startTrace"]
        XCTAssertNoThrow(try backend.recordError(TelemetryError(type: "TestError"), attributes: [:]))
        XCTAssertNoThrow(try backend.breadcrumb(name: "ready", attributes: [:]))
        XCTAssertNoThrow(try backend.setKey("state", value: "ready"))
        let failedID = try backend.startTrace(name: "work", attributes: [:])
        XCTAssertNoThrow(try backend.setAttribute(traceID: failedID, key: "state", value: "ready"))
        XCTAssertNoThrow(try backend.stopTrace(id: failedID, status: .error))
        XCTAssertEqual(Array(sdk.calls.suffix(4)), ["record", "log", "setKey", "startTrace"])
        XCTAssertTrue(sdk.errors.isEmpty)
        XCTAssertTrue(sdk.logs.isEmpty)
        XCTAssertTrue(sdk.traces.isEmpty)
        sdk.failures = []
        try backend.recordError(TelemetryError(type: "TestError"), attributes: [:])
        try backend.breadcrumb(name: "ready", attributes: [:])
        try backend.setKey("state", value: "resumed")
        XCTAssertEqual(sdk.errors.count, 1)
        XCTAssertEqual(sdk.logs, ["ready"])
        XCTAssertEqual(sdk.keys["state"], "resumed")
    }

    func testNilSDKTraceIsInertAndDoesNotPreventLaterTraces() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        sdk.returnsNilTrace = true
        let missing = try backend.startTrace(name: "work", attributes: [:])
        try backend.setAttribute(traceID: missing, key: "state", value: "ready")
        try backend.stopTrace(id: missing, status: .error)
        XCTAssertTrue(sdk.traces.isEmpty)
        sdk.returnsNilTrace = false
        let live = try backend.startTrace(name: "work", attributes: [:])
        try backend.stopTrace(id: live, status: .ok)
        XCTAssertEqual(sdk.traces.count, 1)
        XCTAssertEqual(sdk.traces.first?.stopAttempts, 1)
    }

    func testInitialStatusAndAttributeErrorsStillPermitTraceCleanup() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        sdk.newTraceFailures = ["setValue"]
        let id = try backend.startTrace(name: "work", attributes: ["state": "ready"])
        let trace = try XCTUnwrap(sdk.traces.first)
        XCTAssertEqual(trace.assignments.map { $0.0 }, ["status", "state"])
        XCTAssertTrue(trace.attributes.isEmpty)
        XCTAssertNoThrow(try backend.stopTrace(id: id, status: .error))
        XCTAssertEqual(trace.stopAttempts, 1)
    }

    func testTraceAttributeAndStopErrorsDoNotEscapeOrLeaveLiveHandles() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        let id = try backend.startTrace(name: "work", attributes: [:])
        let trace = try XCTUnwrap(sdk.traces.first)
        trace.failures = ["setValue", "stop"]
        XCTAssertNoThrow(try backend.setAttribute(traceID: id, key: "state", value: "ready"))
        XCTAssertNoThrow(try backend.stopTrace(id: id, status: .error))
        XCTAssertEqual(trace.stopAttempts, 1)
        let assignments = trace.assignments.count
        try backend.setAttribute(traceID: id, key: "state", value: "late")
        try backend.stopTrace(id: id, status: .ok)
        try backend.stop()
        XCTAssertEqual(trace.stopAttempts, 1)
        XCTAssertEqual(trace.assignments.count, assignments)
    }

    func testRestrictionAndStopContinueCleanupWhenEverySDKCleanupCallThrows() async throws {
        for restricting in [false, true] {
            let sdk = RecordingFirebaseTelemetrySDK()
            let backend = FirebaseTelemetryBackend(sdk: sdk, automaticInstrumentationEnabled: true)
            try backend.start()
            try backend.setKey("first", value: "ready")
            try backend.setKey("second", value: "ready")
            let first = try backend.startTrace(name: "work", attributes: [:])
            let second = try backend.startTrace(name: "work", attributes: [:])
            for trace in sdk.traces { trace.failures = ["setValue", "stop"] }
            sdk.failures = ["clearKey", "instrumentation.false", "performance.false", "crash.false", "delete"]
            sdk.calls = []
            if restricting {
                XCTAssertNoThrow(try backend.update(restricted: true))
            } else {
                XCTAssertNoThrow(try backend.stop())
            }
            XCTAssertEqual(sdk.calls.filter { $0 == "clearKey" }.count, 2)
            XCTAssertTrue(sdk.calls.contains("performance.false"))
            XCTAssertTrue(sdk.calls.contains("instrumentation.false"))
            if !restricting {
                XCTAssertTrue(sdk.calls.contains("crash.false"))
                XCTAssertTrue(sdk.calls.contains("delete"))
            }
            XCTAssertEqual(sdk.traces.map(\.stopAttempts), [1, 1])
            try backend.stopTrace(id: first, status: .error)
            try backend.stopTrace(id: second, status: .error)
            XCTAssertEqual(sdk.traces.map(\.stopAttempts), [1, 1])
            sdk.failures = []
            try backend.stop()
            XCTAssertFalse(sdk.crashEnabled)
            XCTAssertFalse(sdk.performanceEnabled)
            XCTAssertFalse(sdk.instrumentationEnabled)
        }
    }
}
