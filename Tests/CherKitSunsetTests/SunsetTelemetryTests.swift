import CherKitCore
@testable import CherKitSunset
import CherKitTestSupport
import Foundation
import XCTest

final class SunsetTelemetryTests: XCTestCase {
    private struct Contract: Decodable {
        let domain: String
        let breadcrumb: String
        let codes: [String: Int]
        let bodies: [String: String]
        let cases: [Case]
    }

    private struct Case: Decodable {
        let name: String
        let config: String
        let lookup: String?
        let cached: String?
        let channel: String?
        let sink: Bool?
        let disabled: Bool?
        let errors: [Int]
        let kind: String
        let cacheSaved: Bool
    }

    @MainActor
    func testSharedTelemetryContract() async throws {
        let fixture = try Fixtures.decode(Contract.self, module: "sunset", name: "telemetry.json")
        XCTAssertEqual(fixture.codes, [
            "config_fetch_failed": 1, "config_timeout": 2, "config_undecodable": 3,
            "store_lookup_failed": 4, "lookup_timeout": 5, "lookup_undecodable": 6
        ])
        XCTAssertFalse(fixture.cases.isEmpty)
        for item in fixture.cases {
            let sink = RecordingSunsetSink()
            let config = try makeSources(item.config, bodies: fixture.bodies).config
            let lookup = try item.lookup.map { try makeSources($0, bodies: fixture.bodies).lookup }
            let cached = try item.cached.map { Data(try XCTUnwrap(fixture.bodies[$0]).utf8) }
            let cache = MemorySunsetCache(cached)
            let checker = SunsetChecker(
                source: config, cache: cache,
                timeout: item.config == "deadline" || item.lookup == "deadline" ? 0.02 : 5,
                platform: .ios, isDisabled: item.disabled ?? false, lookup: lookup,
                telemetrySink: item.sink == false ? nil : sink
            )
            let channel = try XCTUnwrap(SunsetChannel(rawValue: item.channel ?? "store"))
            let decision = await checker.check(
                installedVersion: "1.0", osVersion: "17.0", locale: ["en"], channel: channel, installedBuild: "1"
            )
            XCTAssertEqual(kind(decision), item.kind, item.name)
            XCTAssertEqual(sink.errors.map(\.code), item.errors, item.name)
            for error in sink.errors {
                XCTAssertEqual(error.domain, fixture.domain, item.name)
                XCTAssertTrue(error.attributes.isEmpty, "No response text, URLs or versions: \(item.name)")
            }
            let emits = item.sink != false && item.disabled != true
            XCTAssertEqual(sink.breadcrumbs.count, emits ? 1 : 0, item.name)
            if emits {
                let crumb = try XCTUnwrap(sink.breadcrumbs.first)
                XCTAssertEqual(crumb.name, fixture.breadcrumb, item.name)
                XCTAssertEqual(crumb.attributes.count, 1, item.name)
                XCTAssertEqual(crumb.attributes["kind"] as? String, item.kind, item.name)
            }
            let expectedSave = item.cacheSaved ? [Data(try XCTUnwrap(fixture.bodies[item.config]).utf8)] : []
            XCTAssertEqual(cache.saved, expectedSave, item.name)
            let after = await cache.load()
            XCTAssertEqual(after, expectedSave.first ?? cached, item.name)
            // A source that ignores cancellation may finish after the deadline; that must not emit or cache again.
            if item.config == "deadline" || item.lookup == "deadline" {
                try await Task.sleep(nanoseconds: 200_000_000)
                XCTAssertEqual(sink.errors.map(\.code), item.errors, item.name)
                XCTAssertEqual(sink.breadcrumbs.count, emits ? 1 : 0, item.name)
                XCTAssertEqual(cache.saved, expectedSave, item.name)
            }
        }
    }

    @MainActor
    func testNonPositiveDeadlinesReportTimeoutOncePerAttemptWithoutStartingSources() async {
        for timeout in [0, -1, .nan] as [TimeInterval] {
            let sink = RecordingSunsetSink()
            let source = CountingSunsetSource()
            let checker = SunsetChecker(
                source: source, cache: MemorySunsetCache(), timeout: timeout, lookup: source, telemetrySink: sink
            )
            let result = await checker.check(installedVersion: "1.0", osVersion: "17", locale: [])
            XCTAssertEqual(result, .upToDate)
            XCTAssertEqual(source.calls.with { $0 }, 0)
            XCTAssertEqual(sink.errors.map(\.code), [2, 5])
            XCTAssertEqual(sink.breadcrumbs.count, 1)
        }
    }

    @MainActor
    func testURLConvenienceInitializerAndDefaultNilSink() async {
        let sink = RecordingSunsetSink()
        // A zero deadline avoids real network traffic while covering the URL convenience initializer's forwarding.
        let checker = SunsetChecker(
            url: URL(string: "https://sunset.test/config")!, cache: MemorySunsetCache(), timeout: 0, telemetrySink: sink
        )
        _ = await checker.check(installedVersion: "1.0", osVersion: "17", locale: [])
        XCTAssertEqual(sink.errors.map(\.code), [2])
        XCTAssertEqual(sink.breadcrumbs.count, 1)
        let withoutSink = SunsetChecker(source: TelemetrySource(body: nil), cache: MemorySunsetCache())
        let result = await withoutSink.check(installedVersion: "1.0", osVersion: "17", locale: [])
        XCTAssertEqual(result, .upToDate)
        XCTAssertEqual(sink.errors.count, 1)
    }

    private func makeSources(
        _ outcome: String, bodies: [String: String]
    ) throws -> (config: any SunsetConfigSource, lookup: any SunsetLookupSource) {
        if outcome == "failed" {
            let source = TelemetrySource(body: nil)
            return (source, source)
        }
        if outcome == "deadline" {
            let source = TelemetrySource(body: Data(try XCTUnwrap(bodies["hard"]).utf8), delay: 0.1)
            return (source, source)
        }
        let reply: StubReply
        switch outcome {
        case "network_error": reply = .error
        case "native_timeout": reply = .timeout
        case "http_error": reply = .response(status: 503, body: Data("private server message".utf8))
        case "empty": reply = .response(status: 200, body: Data())
        default: reply = .response(status: 200, body: Data(try XCTUnwrap(bodies[outcome], outcome).utf8))
        }
        let url = StubURLProtocol.serve(reply)
        return (
            URLSessionSunsetConfigSource(url: url, session: StubURLProtocol.session),
            URLSessionSunsetLookupSource(url: url, session: StubURLProtocol.session)
        )
    }

    private func kind(_ decision: SunsetDecision) -> String {
        switch decision {
        case .upToDate: return "none"
        case .softUpdate(let prompt): return prompt.kind == .testBuild ? "test_build" : "soft"
        case .hardUpdate: return "hard"
        }
    }
}

/// An ordinary app-supplied source: only the original optional-data API, including late answers.
private struct TelemetrySource: SunsetConfigSource, SunsetLookupSource {
    let body: Data?
    var delay: TimeInterval = 0

    func fetch(timeout: TimeInterval) async -> Data? {
        if delay > 0 {
            return await SlowSunsetSource(body: body ?? Data(), delay: delay).fetch(timeout: timeout)
        }
        return body
    }
}

private final class CountingSunsetSource: SunsetConfigSource, SunsetLookupSource, Sendable {
    let calls = Locked(0)
    func fetch(timeout: TimeInterval) async -> Data? {
        calls.with { $0 += 1 }
        return nil
    }
}

@MainActor
private final class RecordingSunsetSink: TelemetrySink {
    struct ErrorRecord {
        let domain: String
        let code: Int
        let attributes: [String: Any]
    }
    struct Breadcrumb {
        let name: String
        let attributes: [String: Any]
    }
    var errors: [ErrorRecord] = []
    var breadcrumbs: [Breadcrumb] = []

    func recordError(domain: String, code: Int, attributes: [String: Any]) {
        MainActor.preconditionIsolated()
        errors.append(ErrorRecord(domain: domain, code: code, attributes: attributes))
    }

    func breadcrumb(name: String, attributes: [String: Any]) {
        MainActor.preconditionIsolated()
        breadcrumbs.append(Breadcrumb(name: name, attributes: attributes))
    }
}
