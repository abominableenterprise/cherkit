import CherKitTelemetry
import XCTest
@testable import CherKitTelemetryFirebase

@MainActor
final class FirebaseTelemetryStartupTests: XCTestCase {
    func testConsentingSessionNeverPersistsAutomaticCrashUploadConsent() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        telemetry.enabled = true
        telemetry.recordError(domain: "test.domain", code: 1)
        XCTAssertEqual(sdk.errors.count, 1)
        XCTAssertFalse(sdk.crashEnabled)
        XCTAssertFalse(sdk.calls.contains("crash.true"))
        XCTAssertEqual(sdk.calls.filter { $0 == "send" }.count, 1)
        XCTAssertEqual(sdk.reportDecision, "send", "Initialization must not consume the one-shot decision")
        // Simulate abrupt termination: no stop(), then Firebase configures before the next backend.
        let persistedCrashCollection = sdk.crashEnabled
        XCTAssertFalse(persistedCrashCollection, "Next-launch configure must not auto-upload reports")
        for buildAllowed in [false, true] {
            let nextSDK = RecordingFirebaseTelemetrySDK()
            nextSDK.crashEnabled = persistedCrashCollection
            let next = Telemetry(backend: FirebaseTelemetryBackend(sdk: nextSDK), buildEnabled: buildAllowed)
            next.enabled = false
            next.recordError(domain: "test.domain", code: 2)
            XCTAssertFalse(nextSDK.crashEnabled)
            XCTAssertFalse(nextSDK.calls.contains("send"))
        }
    }

    func testPerformanceOverridesResetBeforeNextLaunchConfigurationAndGates() async throws {
        for restricted in [false, true] {
            for buildAllowed in [false, true] {
                let sdk = RecordingFirebaseTelemetrySDK()
                let backend = FirebaseTelemetryBackend(sdk: sdk, automaticInstrumentationEnabled: true)
                try backend.start()
                XCTAssertTrue(sdk.performanceEnabled)
                XCTAssertTrue(sdk.instrumentationEnabled)
                // Same SDK state models persisted overrides after abrupt termination, without stop().
                sdk.calls = []
                try FirebaseTelemetryBackend.prepareForConfiguration(sdk: sdk)
                XCTAssertEqual(sdk.calls, ["performance.false", "instrumentation.false"])
                // FirebaseApp.configure() runs here, before current consent/restriction is applied.
                XCTAssertFalse(sdk.performanceEnabled)
                XCTAssertFalse(sdk.instrumentationEnabled)
                XCTAssertFalse(sdk.calls.contains("delete"))
                let next = Telemetry(backend: FirebaseTelemetryBackend(sdk: sdk), buildEnabled: buildAllowed)
                next.restricted = restricted
                next.enabled = buildAllowed
                XCTAssertEqual(sdk.performanceEnabled, buildAllowed && !restricted)
                XCTAssertFalse(sdk.instrumentationEnabled)
                next.enabled = false
            }
        }
    }

    func testFailedStartupResetMustPreventFirebaseConfiguration() async {
        for failure in ["performance.false", "instrumentation.false"] {
            let sdk = RecordingFirebaseTelemetrySDK()
            sdk.failures = [failure]
            XCTAssertThrowsError(try FirebaseTelemetryBackend.prepareForConfiguration(sdk: sdk))
        }
    }
}
