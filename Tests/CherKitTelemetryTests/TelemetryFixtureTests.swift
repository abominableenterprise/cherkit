import CherKitCore
import CherKitTelemetry
import CherKitTestSupport
import XCTest

@MainActor
final class TelemetryFixtureTests: XCTestCase {
    private struct Gate: Decodable {
        let buildEnabled: Bool
        let enabled: Bool
        let restricted: Bool
        let errors: Int
        let diagnostics: Int
    }
    private struct Validation: Decodable {
        let name: String
        let validName: Bool
        let kind: String
        let value: String
        let validValue: Bool
    }
    private struct Lifecycle: Decodable {
        let name: String
        let steps: [String]
        let expected: [String]
    }

    func testEveryGateFixture() throws {
        let cases = try Fixtures.decode([Gate].self, module: "telemetry", name: "gates.json")
        XCTAssertEqual(cases.count, 8)
        for (index, item) in cases.enumerated() {
            let backend = RecordingBackend()
            let telemetry = Telemetry(backend: backend, buildEnabled: item.buildEnabled)
            telemetry.enabled = item.enabled
            telemetry.restricted = item.restricted
            XCTAssertEqual(telemetry.isDisabled, !item.buildEnabled || !item.enabled)
            telemetry.recordError(domain: "test", code: 1)
            telemetry.breadcrumb(name: "ready")
            telemetry.setKey("state", value: "ready")
            let span = telemetry.startSpan(name: "work")
            span.setAttribute("state", value: "ready")
            span.end()
            let label = "gate \(index)"
            XCTAssertEqual(backend.errors.count, item.errors, label)
            XCTAssertEqual(backend.breadcrumbs.count, item.diagnostics, label)
            XCTAssertEqual(backend.keys.count, item.diagnostics, label)
            XCTAssertEqual(backend.traceNames.count, item.diagnostics, label)
            XCTAssertEqual(backend.traceAttributes.count, item.diagnostics, label)
            XCTAssertEqual(backend.stops.count, item.diagnostics, label)
        }
    }

    func testEveryValidationFixture() throws {
        let cases = try Fixtures.decode([Validation].self, module: "telemetry", name: "validation.json")
        XCTAssertFalse(cases.isEmpty)
        for item in cases {
            let backend = RecordingBackend()
            let telemetry = Telemetry(backend: backend, buildEnabled: true)
            let value = try fixtureValue(item)
            telemetry.breadcrumb(name: item.name, attributes: ["value": value, item.name: "safe"])
            XCTAssertEqual(backend.breadcrumbs.count, item.validName ? 1 : 0, item.name)
            if item.validName {
                let attributes = try XCTUnwrap(backend.attributes.last)
                XCTAssertEqual(attributes["value"] != nil, item.validValue, item.name)
                XCTAssertNotNil(attributes[item.name], item.name)
            }
            telemetry.setKey(item.name, value: value)
            XCTAssertEqual(backend.keys.count, item.validName && item.validValue ? 1 : 0, item.name)
            let span = telemetry.startSpan(name: item.name, attributes: ["value": value])
            span.setAttribute(item.name, value: value)
            span.end()
            XCTAssertEqual(backend.traceNames.count, item.validName ? 1 : 0, item.name)
            XCTAssertEqual(backend.traceAttributes.count, item.validName && item.validValue ? 1 : 0, item.name)
            telemetry.recordError(domain: "test", code: 1, attributes: ["value": value, item.name: "safe"])
            let filtered = try XCTUnwrap(backend.attributes.last)
            XCTAssertEqual(filtered["value"] != nil, item.validValue, item.name)
            XCTAssertEqual(filtered[item.name] != nil, item.validName, item.name)
            if item.kind == "version", item.validValue {
                XCTAssertEqual(filtered["value"] as? AppVersion, AppVersion(item.value))
            }
        }
    }

    private func fixtureValue(_ item: Validation) throws -> Any {
        switch item.kind {
        case "string": return item.value
        case "bool": return try XCTUnwrap(Bool(item.value))
        case "int": return try XCTUnwrap(Int(item.value))
        case "double": return try XCTUnwrap(Double(item.value))
        case "version": return try XCTUnwrap(AppVersion(item.value))
        case "array": return ["safe"]
        case "object": return ["safe": "safe"]
        case "nan": return Double.nan
        default: XCTFail("Unknown fixture kind: \(item.kind)"); return ()
        }
    }

    func testEveryLifecycleFixture() throws {
        let cases = try Fixtures.decode([Lifecycle].self, module: "telemetry", name: "lifecycle.json")
        XCTAssertFalse(cases.isEmpty)
        for item in cases {
            let backend = RecordingBackend()
            let telemetry = Telemetry(backend: backend, buildEnabled: true)
            for step in item.steps {
                switch step {
                case "error": telemetry.recordError(domain: "test", code: 1)
                case "disable": telemetry.enabled = false
                case "enable": telemetry.enabled = true
                case "restrict": telemetry.restricted = true
                case "unrestrict": telemetry.restricted = false
                case "breadcrumb": telemetry.breadcrumb(name: "ready")
                case "span": telemetry.startSpan(name: "work").end()
                case "key": telemetry.setKey("state", value: "ready")
                default: XCTFail("Unknown fixture step: \(step)")
                }
            }
            XCTAssertEqual(backend.calls, item.expected, item.name)
        }
    }
}
