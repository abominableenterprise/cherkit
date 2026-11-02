import CherKitCore
import CherKitTelemetry
import Foundation
import XCTest
@testable import CherKitTelemetryFirebase

@MainActor
final class FirebaseTelemetryLimitTests: XCTestCase {
    func testTraceReservesStatusAndSelectsFourSortedValidExtras() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        let id = try backend.startTrace(name: "work", attributes: [
            "echo": "fifth", "delta": "fourth", "charlie": "third",
            "bravo": "second", "alpha": "first", "status": "spoofed"
        ])
        let trace = try XCTUnwrap(sdk.traces.first)
        XCTAssertEqual(trace.attributes, [
            "status": "ok", "alpha": "first", "bravo": "second", "charlie": "third", "delta": "fourth"
        ])
        XCTAssertEqual(trace.assignments.first?.0, "status")
        XCTAssertEqual(trace.assignments.first?.1, "ok")
        try backend.setAttribute(traceID: id, key: "echo", value: "overflow")
        try backend.setAttribute(traceID: id, key: "alpha", value: "replacement")
        try backend.setAttribute(traceID: id, key: "status", value: "spoofed")
        XCTAssertEqual(trace.attributes.count, 5)
        XCTAssertNil(trace.attributes["echo"])
        XCTAssertEqual(trace.attributes["alpha"], "replacement")
        XCTAssertEqual(trace.attributes["status"], "ok")
        try backend.stopTrace(id: id, status: .error)
        XCTAssertEqual(trace.attributes["status"], "error")
        XCTAssertEqual(trace.stopAttempts, 1)
    }

    func testAttributeNamesUseFirebaseSubsetForInitialAndLaterAttributes() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        let invalid = [
            "", "_private", "firebase_state", "google_state", "ga_state", "state1", "state.name",
            "state-name", "é", "state\n", String(repeating: "a", count: 41)
        ]
        for key in invalid {
            let id = try backend.startTrace(name: "work", attributes: [key: "ready"])
            let trace = try XCTUnwrap(sdk.traces.last)
            try backend.setAttribute(traceID: id, key: key, value: "ready")
            XCTAssertEqual(trace.attributes, ["status": "ok"], "invalid attribute: \(key.debugDescription)")
            try backend.stopTrace(id: id, status: .ok)
        }
        let boundary = String(repeating: "a", count: 40)
        let id = try backend.startTrace(name: "work", attributes: [boundary: "ready", "state_name": "ready"])
        let trace = try XCTUnwrap(sdk.traces.last)
        XCTAssertEqual(trace.attributes[boundary], "ready")
        XCTAssertEqual(trace.attributes["state_name"], "ready")
        try backend.stopTrace(id: id, status: .ok)
    }

    func testSupportedValuesAreConvertedAndInvalidValuesRejectedOnEveryPath() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        let version = try XCTUnwrap(AppVersion("1.2.3"))
        let values: [(Any, String)] = [(true, "true"), (4, "4"), (1.25, "1.25"), ("ready", "ready"),
                                         (version, "1.2.3")]
        for (value, expected) in values {
            try backend.setKey("state", value: value)
            let id = try backend.startTrace(name: "work", attributes: ["initial": value])
            try backend.setAttribute(traceID: id, key: "later", value: value)
            let trace = try XCTUnwrap(sdk.traces.last)
            XCTAssertEqual(sdk.keys["state"], expected)
            XCTAssertEqual(trace.attributes["initial"], expected)
            XCTAssertEqual(trace.attributes["later"], expected)
            try backend.stopTrace(id: id, status: .ok)
        }

        let invalid: [Any] = [Double.nan, Double.infinity, -Double.infinity, Float(1), UInt(1),
                              NSNumber(value: 1), NSNull(), [1], ["state": "ready"], "", "private text",
                              "é", String(repeating: "a", count: 65)]
        for value in invalid {
            try backend.setKey("rejected", value: value)
            let id = try backend.startTrace(name: "work", attributes: ["initial": value])
            try backend.setAttribute(traceID: id, key: "later", value: value)
            XCTAssertNil(sdk.keys["rejected"])
            XCTAssertEqual(sdk.traces.last?.attributes, ["status": "ok"])
            try backend.stopTrace(id: id, status: .ok)
        }
    }

    func testConvertedTraceValuesRespectOneHundredByteLimitWithoutTruncation() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        // AppVersion is core-valid even when its serialized representation exceeds string limits.
        let justOverLimit = try XCTUnwrap(AppVersion(Array(repeating: "123456789", count: 10).joined(separator: ".") + ".1"))
        let overLimit = try XCTUnwrap(AppVersion(Array(repeating: "123456789", count: 11).joined(separator: ".")))
        let accepted = try XCTUnwrap(AppVersion((Array(repeating: "123456789", count: 9) + ["12345678", "1"]).joined(separator: ".")))
        XCTAssertEqual(accepted.description.utf8.count, 100)
        XCTAssertEqual(justOverLimit.description.utf8.count, 101)
        for value in [justOverLimit, overLimit] {
            let id = try backend.startTrace(name: "work", attributes: ["initial": value])
            try backend.setAttribute(traceID: id, key: "later", value: value)
            XCTAssertEqual(sdk.traces.last?.attributes, ["status": "ok"])
            try backend.stopTrace(id: id, status: .ok)
        }
        try backend.setKey("version", value: accepted)
        let id = try backend.startTrace(name: "work", attributes: ["version": accepted])
        XCTAssertEqual(sdk.keys["version"], accepted.description)
        XCTAssertEqual(sdk.traces.last?.attributes["version"], accepted.description)
        try backend.stopTrace(id: id, status: .ok)
    }

    func testGlobalKeyCapacityAllowsReplacementAndIsResetByStop() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        let names = (0..<64).map { index in
            "key" + String(UnicodeScalar(97 + index / 26)!) + String(UnicodeScalar(97 + index % 26)!)
        }
        for key in names { try backend.setKey(key, value: "ready") }
        XCTAssertEqual(sdk.keys.count, 64)
        try backend.setKey("overflow", value: "blocked")
        XCTAssertNil(sdk.keys["overflow"])
        try backend.setKey(names[0], value: "replacement")
        XCTAssertEqual(sdk.keys[names[0]], "replacement")
        XCTAssertEqual(sdk.keys.count, 64)
        try backend.stop()
        XCTAssertTrue(sdk.keys.isEmpty)
        try backend.start()
        try backend.setKey("overflow", value: "resumed")
        XCTAssertEqual(sdk.keys, ["overflow": "resumed"])
    }

    func testDirectTraceNamesStillRequireCoreSafeNames() async throws {
        let sdk = RecordingFirebaseTelemetrySDK()
        let backend = FirebaseTelemetryBackend(sdk: sdk)
        try backend.start()
        let calls = sdk.calls
        for name in ["", "Bad", "_private", "name\n", "a-b", "é", String(repeating: "a", count: 65)] {
            let id = try backend.startTrace(name: name, attributes: [:])
            try backend.stopTrace(id: id, status: .ok)
        }
        XCTAssertEqual(sdk.calls, calls)
        let name = String(repeating: "a", count: 64)
        let id = try backend.startTrace(name: name, attributes: [:])
        XCTAssertEqual(sdk.traces.last?.name, name)
        try backend.stopTrace(id: id, status: .ok)
    }
}
