@testable import CherKitSunset
import Foundation
import XCTest

/// ``SunsetChecker/disabled``: always `upToDate`, with no network and no cache.
final class SunsetDisabledCheckerTests: XCTestCase {
    func testAlwaysUpToDate() async {
        for (installed, os) in [("0.0.1", "1.0"), ("1.0", "17.0"), ("", ""), ("dev", "unknown")] {
            let decision = await SunsetChecker.disabled.check(installedVersion: installed, osVersion: os, locale: ["en"])
            XCTAssertEqual(decision, .upToDate, "\(installed) on \(os)")
        }
        let launch = await SunsetChecker.disabled.check()
        XCTAssertEqual(launch, .upToDate)
    }

    func testTouchesNeitherTheNetworkNorTheDefaultCache() async {
        let checker = SunsetChecker.disabled
        XCTAssertFalse(checker.source is URLSessionSunsetConfigSource)
        XCTAssertFalse(checker.cache is UserDefaultsSunsetConfigCache)
        let fetched = await checker.source.fetch(timeout: 1)
        let cached = await checker.cache.load()
        XCTAssertNil(fetched)
        XCTAssertNil(cached)
        await checker.cache.save(Data("{}".utf8))
        let cachedAfterSave = await checker.cache.load()
        XCTAssertNil(cachedAfterSave)
    }

    func testOrdinaryCheckersAreNotDisabled() async {
        let hard = Data(#"{"schemaVersion": 1, "ios": {"minimumVersion": "2.0", "storeURL": "https://apps.apple.com/app/id1"}}"#.utf8)
        let checker = SunsetChecker(source: SlowSunsetSource(body: hard, delay: 0), cache: MemorySunsetCache())
        XCTAssertFalse(checker.isDisabled)
        let decision = await checker.check(installedVersion: "1.0", osVersion: "17.0", locale: ["en"])
        guard case .hardUpdate = decision else { return XCTFail("expected a hard block, got \(decision)") }
    }
}
