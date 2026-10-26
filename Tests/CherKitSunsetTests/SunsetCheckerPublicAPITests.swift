// A plain (not `@testable`) import, so these tests see only what an app sees.
import CherKitSunset
import Foundation
import XCTest

/// What app code (and app tests) can read from ``SunsetChecker`` without `@testable`, plus the failure flag the
/// CherKitUI runner uses to retry.
final class SunsetCheckerPublicAPITests: XCTestCase {
    private let hard = Data(#"{"schemaVersion": 1, "ios": {"minimumVersion": "2.0", "storeURL": "https://apps.apple.com/app/id1"}}"#.utf8)

    func testIsDisabledIsPublic() {
        XCTAssertTrue(SunsetChecker.disabled.isDisabled)
        XCTAssertFalse(SunsetChecker(url: URL(string: "https://example.com/sunset.json")!).isDisabled)
        XCTAssertFalse(SunsetChecker(source: FixedSource(body: nil)).isDisabled)
    }

    func testCheckFailsOnlyWithNoUsableConfig() async {
        let cases: [(fetched: Data?, cached: Data?, failed: Bool, label: String)] = [
            (nil, nil, true, "fetch failed, nothing cached"),
            (Data("{\"schemaVersion\": 1, \"ios\":".utf8), nil, true, "truncated body, nothing cached"),
            (nil, Data("not json".utf8), true, "fetch failed, cache unusable"),
            (nil, hard, false, "fetch failed, cache usable"),
            (hard, nil, false, "fetched"),
        ]
        for item in cases {
            let checker = SunsetChecker(source: FixedSource(body: item.fetched), cache: FixedCache(body: item.cached))
            let result = await checker.checkReportingFailure(installedVersion: "1.0", osVersion: "17.0", locale: ["en"])
            XCTAssertEqual(result.failed, item.failed, item.label)
            let decision = await checker.check(installedVersion: "1.0", osVersion: "17.0", locale: ["en"])
            XCTAssertEqual(result.decision, decision, item.label)
            if item.failed { XCTAssertEqual(decision, .upToDate, "fails open: \(item.label)") }
        }
    }

    func testDisabledNeverFails() async {
        let result = await SunsetChecker.disabled.checkReportingFailure(installedVersion: "1.0", osVersion: "17.0", locale: [])
        XCTAssertEqual(result.decision, .upToDate)
        XCTAssertFalse(result.failed)
    }
}

private struct FixedSource: SunsetConfigSource {
    let body: Data?
    func fetch(timeout: TimeInterval) async -> Data? { body }
}

private struct FixedCache: SunsetConfigCache {
    let body: Data?
    func load() async -> Data? { body }
    func save(_ data: Data) async {}
}
