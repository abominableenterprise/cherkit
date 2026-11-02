import CherKitTelemetry
import Foundation
import XCTest
@testable import CherKitTelemetryFirebase

@MainActor
final class FirebaseTelemetryPrivacyTests: XCTestCase {
    func testEveryBuildUserAndRestrictionCombinationThroughFacade() async {
        for buildEnabled in [false, true] {
            for userEnabled in [false, true] {
                for restricted in [false, true] {
                    let sdk = RecordingFirebaseTelemetrySDK()
                    let backend = FirebaseTelemetryBackend(sdk: sdk, automaticInstrumentationEnabled: true)
                    let telemetry = Telemetry(backend: backend, buildEnabled: buildEnabled)
                    telemetry.restricted = restricted
                    telemetry.enabled = userEnabled
                    telemetry.recordError(domain: "test.domain", code: 7, attributes: ["state": "ready"])
                    telemetry.breadcrumb(name: "ready")
                    telemetry.setKey("state", value: "ready")
                    telemetry.startSpan(name: "work").end()

                    let collecting = buildEnabled && userEnabled
                    let diagnostics = collecting && !restricted
                    let context = "build=\(buildEnabled), enabled=\(userEnabled), restricted=\(restricted)"
                    XCTAssertEqual(sdk.errors.count, collecting ? 1 : 0, context)
                    XCTAssertEqual(sdk.logs.count, diagnostics ? 1 : 0, context)
                    XCTAssertEqual(sdk.keys["state"], diagnostics ? "ready" : nil, context)
                    XCTAssertEqual(sdk.traces.count, diagnostics ? 1 : 0, context)
                    XCTAssertFalse(sdk.crashEnabled, context)
                    XCTAssertEqual(sdk.calls.contains("send"), collecting, context)
                    XCTAssertEqual(sdk.performanceEnabled, diagnostics, context)
                    XCTAssertEqual(sdk.instrumentationEnabled, diagnostics, context)
                    telemetry.enabled = false
                }
            }
        }
    }

    func testErrorsAreRebuiltWithoutUserInfoOrAttributeSideChannels() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let source = NSError(domain: "test.domain", code: -23, userInfo: [
            NSLocalizedDescriptionKey: "private description",
            NSUnderlyingErrorKey: NSError(domain: "private.domain", code: 99),
            "payload": "private payload"
        ])
        telemetry.recordError(source, attributes: ["message": "secret", "type": "secret", "state": "ready"])
        let recorded = try XCTUnwrap(sdk.errors.first)
        XCTAssertFalse(recorded === source)
        XCTAssertEqual(recorded.domain, "test.domain")
        XCTAssertEqual(recorded.code, -23)
        XCTAssertTrue(recorded.userInfo.isEmpty)
        XCTAssertTrue(sdk.keys.isEmpty)
        XCTAssertTrue(sdk.logs.isEmpty)
        XCTAssertTrue(sdk.traces.isEmpty)

        try backend.recordError(TelemetryError(type: "FallbackError"), attributes: ["state": "ready"])
        let fallback = try XCTUnwrap(sdk.errors.last)
        XCTAssertEqual(fallback.domain, "FallbackError")
        XCTAssertEqual(fallback.code, 0)
        XCTAssertTrue(fallback.userInfo.isEmpty)
        XCTAssertTrue(sdk.keys.isEmpty)
    }

    func testRestrictionClearsExistingKeysEndsTracesAndAllowsOnlyErrors() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk, automaticInstrumentationEnabled: true)
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        telemetry.setKey("state", value: "ready")
        telemetry.breadcrumb(name: "ready")
        let span = telemetry.startSpan(name: "work", attributes: ["state": "ready"])
        let trace = try XCTUnwrap(sdk.traces.first)
        telemetry.restricted = true
        XCTAssertTrue(sdk.keys.isEmpty)
        XCTAssertEqual(trace.stopAttempts, 1)
        XCTAssertFalse(sdk.crashEnabled)
        XCTAssertFalse(sdk.performanceEnabled)
        XCTAssertFalse(sdk.instrumentationEnabled)
        let calls = sdk.calls
        span.setAttribute("state", value: "late")
        span.end(status: .error)
        telemetry.setKey("state", value: "blocked")
        telemetry.breadcrumb(name: "blocked")
        telemetry.startSpan(name: "blocked").end()
        // Also exercise the backend directly: the facade must not be its only privacy gate.
        try backend.setKey("state", value: "blocked")
        try backend.breadcrumb(name: "blocked", attributes: [:])
        let blockedID = try backend.startTrace(name: "blocked", attributes: [:])
        try backend.setAttribute(traceID: blockedID, key: "state", value: "blocked")
        try backend.stopTrace(id: blockedID, status: .error)
        XCTAssertEqual(sdk.calls, calls)
        XCTAssertEqual(trace.stopAttempts, 1)
        telemetry.recordError(domain: "test.domain", code: 4, attributes: ["state": "ready"])
        XCTAssertEqual(sdk.errors.count, 1)
        XCTAssertTrue(sdk.keys.isEmpty)
        // Firebase cannot clear breadcrumb history: restriction prevents new logs, not old ones.
        XCTAssertEqual(sdk.logs.count, 1)

        telemetry.restricted = false
        XCTAssertTrue(sdk.performanceEnabled)
        XCTAssertTrue(sdk.instrumentationEnabled)
        telemetry.setKey("state", value: "resumed")
        telemetry.startSpan(name: "resumed").end()
        span.setAttribute("state", value: "stale")
        XCTAssertEqual(sdk.keys["state"], "resumed")
        XCTAssertEqual(trace.stopAttempts, 1)
        XCTAssertEqual(sdk.traces.count, 2)
    }

    func testBreadcrumbAttributesNeverBecomeLogTextOrGlobalKeys() async {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        telemetry.breadcrumb(name: "ready", attributes: ["message": "secret", "state": "ready"])
        XCTAssertEqual(sdk.logs, ["ready"])
        XCTAssertTrue(sdk.keys.isEmpty)
    }

    func testRestrictionBeforeStartDoesNotEnablePerformanceEvenBriefly() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk, automaticInstrumentationEnabled: true)
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        telemetry.restricted = true
        telemetry.recordError(domain: "test.domain", code: 1)
        XCTAssertFalse(sdk.crashEnabled)
        XCTAssertFalse(sdk.calls.contains("performance.true"))
        XCTAssertFalse(sdk.calls.contains("instrumentation.true"))
        XCTAssertTrue(sdk.keys.isEmpty)
        XCTAssertTrue(sdk.traces.isEmpty)
    }
}
