import CherKitCore
import CherKitSunset
import CherKitTestSupport
import Foundation
import XCTest

/// Runs every case in `fixtures/sunset/update-sources.json`; Kotlin's `SunsetUpdateSourcesFixtureTest` runs the same
/// cases.
final class SunsetUpdateSourcesFixtureTests: XCTestCase {
    private struct Defaults: Decodable {
        let platform: String
        let channel: String
        let appVersion: String
        let osVersion: String
        let languages: [String]
        let now: String
    }

    private struct SourcesCase: Decodable {
        let name: String
        let config: String?
        let lookup: String?
        let platform: String?
        let channel: String?
        let installedBuild: String?
        let appVersion: String?
        let osVersion: String?
        let languages: [String]?
        let now: String?
        let decision: String
        let kind: String?
        let storeURL: String?
        let message: String?
        let latestVersion: String?
        let latestBuild: String?
    }

    private struct Fixture: Decodable {
        let defaults: Defaults
        let cases: [SourcesCase]
    }

    func testUpdateSourcesCases() throws {
        let data = try Fixtures.data(module: "sunset", name: "update-sources.json")
        let fixture = try JSONDecoder().decode(Fixture.self, from: data)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let configs = try XCTUnwrap(root["configs"] as? [String: Any])
        let lookups = try XCTUnwrap(root["lookups"] as? [String: Any])
        let defaults = fixture.defaults
        XCTAssertFalse(fixture.cases.isEmpty)
        for testCase in fixture.cases {
            let config = try testCase.config.map { name in
                try JSONSerialization.data(withJSONObject: try XCTUnwrap(configs[name], testCase.name))
            }
            let lookup = try testCase.lookup.map { name in
                let value = try XCTUnwrap(lookups[name], testCase.name)
                return try JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed])
            }
            let decision = SunsetDecider.decide(
                config: config.flatMap(SunsetConfig.parse),
                platform: try XCTUnwrap(SunsetPlatform(rawValue: testCase.platform ?? defaults.platform), testCase.name),
                appVersion: testCase.appVersion ?? defaults.appVersion,
                osVersion: testCase.osVersion ?? defaults.osVersion,
                languages: testCase.languages ?? defaults.languages,
                channel: try XCTUnwrap(SunsetChannel(rawValue: testCase.channel ?? defaults.channel), testCase.name),
                installedBuild: testCase.installedBuild,
                lookup: lookup.flatMap(SunsetLookupResult.parse),
                now: try XCTUnwrap(SunsetFixtureTime.parse(testCase.now ?? defaults.now), testCase.name)
            )
            XCTAssertEqual(decision, try expected(testCase), testCase.name)
        }
    }

    private func expected(_ testCase: SourcesCase) throws -> SunsetDecision {
        if testCase.decision == "upToDate" { return .upToDate }
        let prompt = SunsetPrompt(
            storeURL: try XCTUnwrap(URL(string: try XCTUnwrap(testCase.storeURL, testCase.name)), testCase.name),
            message: testCase.message,
            latestVersion: try testCase.latestVersion.map { try XCTUnwrap(AppVersion($0), testCase.name) },
            kind: try XCTUnwrap(SunsetUpdateKind(rawValue: try XCTUnwrap(testCase.kind, testCase.name)), testCase.name),
            latestBuild: try testCase.latestBuild.map { try XCTUnwrap(SunsetBuildNumber($0), testCase.name) }
        )
        switch testCase.decision {
        case "softUpdate": return .softUpdate(prompt)
        case "hardUpdate": return .hardUpdate(prompt)
        default:
            XCTFail("unknown decision \(testCase.decision) in \(testCase.name)")
            return .upToDate
        }
    }
}

/// Parses the fixtures' `now` values (`YYYY-MM-DDTHH:MM:SSZ`) independently of the code under test.
enum SunsetFixtureTime {
    static func parse(_ text: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: text)
    }
}
