import CherKitSunset
import CherKitTestSupport
import Foundation
import XCTest

/// Runs every case in `fixtures/sunset/checker.json` through ``SunsetChecker`` with the real
/// `URLSessionSunsetConfigSource` on a stubbed `URLSession`; Kotlin's `SunsetCheckerFixtureTest` runs the same cases.
final class SunsetCheckerFixtureTests: XCTestCase {
    private struct CheckerFixture: Decodable {
        let cases: [CheckerCase]
        let defaults: Defaults
    }

    private struct Defaults: Decodable {
        let installedVersion: String
        let osVersion: String
        let locale: [String]
    }

    private struct Fetch: Decodable {
        let result: String
        let status: Int?
        let config: String?
        let text: String?
    }

    private struct CheckerCase: Decodable {
        let name: String
        let platform: String
        let installedVersion: String?
        let osVersion: String?
        let fetch: Fetch
        let cached: String?
        let cachedText: String?
        let decision: String
        let storeURL: String?
        let cacheAfter: String
    }

    func testCheckerCases() async throws {
        let data = try Fixtures.data(module: "sunset", name: "checker.json")
        let fixture = try JSONDecoder().decode(CheckerFixture.self, from: data)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let configs = try XCTUnwrap(root["configs"] as? [String: Any])
        func body(_ name: String?, text: String?) throws -> Data? {
            guard let name else { return text.map { Data($0.utf8) } }
            return try JSONSerialization.data(withJSONObject: try XCTUnwrap(configs[name], name))
        }
        XCTAssertFalse(fixture.cases.isEmpty)

        for testCase in fixture.cases {
            let fetchedBody = try body(testCase.fetch.config, text: testCase.fetch.text)
            let reply: StubReply
            switch testCase.fetch.result {
            case "response": reply = .response(status: try XCTUnwrap(testCase.fetch.status), body: fetchedBody ?? Data())
            case "timeout": reply = .hang
            case "error": reply = .error
            default: return XCTFail("unknown fetch result in \(testCase.name)")
            }
            let source = URLSessionSunsetConfigSource(url: StubURLProtocol.serve(reply), session: StubURLProtocol.session)
            let cached = try body(testCase.cached, text: testCase.cachedText)
            let cache = MemorySunsetCache(cached)
            let checker = SunsetChecker(
                source: source,
                cache: cache,
                timeout: testCase.fetch.result == "timeout" ? 0.3 : 10,
                platform: try XCTUnwrap(SunsetPlatform(rawValue: testCase.platform))
            )

            let decision = await checker.check(
                installedVersion: testCase.installedVersion ?? fixture.defaults.installedVersion,
                osVersion: testCase.osVersion ?? fixture.defaults.osVersion,
                locale: fixture.defaults.locale
            )

            XCTAssertEqual(name(of: decision), testCase.decision, testCase.name)
            XCTAssertEqual(decision.prompt?.storeURL.absoluteString, testCase.storeURL, testCase.name)
            XCTAssertEqual(cache.saved, testCase.cacheAfter == "fetched" ? [try XCTUnwrap(fetchedBody)] : [], testCase.name)
            let cachedAfter = await cache.load()
            XCTAssertEqual(cachedAfter, testCase.cacheAfter == "fetched" ? fetchedBody : cached, testCase.name)
        }
    }

    private func name(of decision: SunsetDecision) -> String {
        switch decision {
        case .upToDate: return "upToDate"
        case .softUpdate: return "softUpdate"
        case .hardUpdate: return "hardUpdate"
        }
    }
}
