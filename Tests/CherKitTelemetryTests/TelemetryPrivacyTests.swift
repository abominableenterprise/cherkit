import CherKitCore
import CherKitTelemetry
import Foundation
import XCTest

@MainActor
final class TelemetryPrivacyTests: XCTestCase {
    private struct PrivateError: Error, CustomNSError, LocalizedError {
        static var errorDomain: String { "test.domain" }
        var errorCode: Int { 17 }
        var errorUserInfo: [String: Any] { ["private_payload": "private text"] }
        var errorDescription: String? { "private description" }
    }

    func testErrorIsReducedToIdentityAndAttributesAreFiltered() throws {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let error = PrivateError()
        telemetry.recordError(error, attributes: ["state": "ready", "message": "private text", "raw": error])
        let recorded = try XCTUnwrap(backend.errors.first)
        XCTAssertEqual(recorded.type, String(reflecting: PrivateError.self))
        XCTAssertEqual(recorded.domain, "test.domain")
        XCTAssertEqual(recorded.code, 17)
        XCTAssertEqual(backend.attributes.first?.count, 1)
        XCTAssertEqual(backend.attributes.first?["state"] as? String, "ready")
        XCTAssertFalse(String(reflecting: recorded).contains("private text"))
        XCTAssertFalse(String(reflecting: recorded).contains("private description"))
    }

    func testNSErrorAndDomainCodeOverloadThroughCoreSink() throws {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let error = NSError(domain: "test.domain", code: 42,
                            userInfo: [NSLocalizedDescriptionKey: "private text"])
        telemetry.recordError(error)
        let sink: any TelemetrySink = telemetry
        sink.recordError(domain: "other.domain", code: -1, attributes: [:])
        sink.breadcrumb(name: "ready", attributes: [:])
        XCTAssertEqual(backend.errors, [TelemetryError(type: String(reflecting: Swift.type(of: error)),
                                                      domain: "test.domain", code: 42),
                                        TelemetryError(type: "Error", domain: "other.domain", code: -1)])
        XCTAssertEqual(backend.breadcrumbs, ["ready"])
    }

    func testOnlyExactAllowlistedTypesAndFiniteDoublesPassEveryAttributePath() throws {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let version = try XCTUnwrap(AppVersion("1.2.3"))
        let values: [String: Any] = [
            "bool": true, "int": 4, "double": 1.25, "string": "ready-1", "version": version,
            "nan": Double.nan, "infinity": Double.infinity, "negative_infinity": -Double.infinity,
            "float": Float(1), "number": NSNumber(value: 1), "unsigned": UInt(1),
            "array": [1], "object": ["state": "ready"], "null": NSNull(),
            "unicode": "é", "newline": "ready\n", "Bad": "ready", "name\n": "ready"
        ]
        let expected: Set<String> = ["bool", "int", "double", "string", "version"]
        telemetry.recordError(domain: "test", code: 1, attributes: values)
        telemetry.breadcrumb(name: "ready", attributes: values)
        let span = telemetry.startSpan(name: "work", attributes: values)
        for (key, value) in values {
            telemetry.setKey(key, value: value)
            span.setAttribute(key, value: value)
        }
        span.end()
        XCTAssertEqual(backend.attributes.count, 3)
        for attributes in backend.attributes {
            XCTAssertEqual(Set(attributes.keys), expected)
            XCTAssertEqual(attributes["version"] as? AppVersion, version)
        }
        XCTAssertEqual(Set(backend.keys.map { $0.0 }), expected)
        XCTAssertEqual(Set(backend.traceAttributes.map { $0.1 }), expected)
    }

    func testInvalidNamesDoNotStartBackend() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        for name in ["", "Bad", "é", "a-b", "a\n", "a\r", "a\0", String(repeating: "a", count: 65)] {
            telemetry.breadcrumb(name: name)
            telemetry.setKey(name, value: "ready")
            telemetry.startSpan(name: name).end()
        }
        XCTAssertTrue(backend.calls.isEmpty)
    }
}
