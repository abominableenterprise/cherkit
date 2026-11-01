import CherKitSunset
import Foundation
import XCTest

/// How ``SunsetChecker`` asks its optional App Store lookup: only on the store channel, within the same deadline.
final class SunsetCheckerLookupTests: XCTestCase {
    private let config = Data(#"{"schemaVersion": 1, "ios": {"latestVersion": "1.4", "storeURL": "https://apps.apple.com/app/id1", "testFlight": {"latestBuild": "9"}}}"#.utf8)
    private let lookupBody = Data(#"{"resultCount": 1, "results": [{"version": "2.0", "minimumOsVersion": "15.0"}]}"#.utf8)

    private struct CountingLookup: SunsetLookupSource {
        let body: Data?
        let calls = Locked(0)

        func fetch(timeout: TimeInterval) async -> Data? {
            calls.with { $0 += 1 }
            return body
        }
    }

    private struct FixedSource: SunsetConfigSource {
        let body: Data

        func fetch(timeout: TimeInterval) async -> Data? { body }
    }

    private func checker(lookup: SunsetLookupSource?, timeout: TimeInterval = 5) -> SunsetChecker {
        SunsetChecker(source: FixedSource(body: config), cache: MemorySunsetCache(), timeout: timeout, lookup: lookup)
    }

    func testLookupVersionIsOfferedOnTheStoreChannel() async {
        let lookup = CountingLookup(body: lookupBody)
        let decision = await checker(lookup: lookup).check(installedVersion: "1.4", osVersion: "17.0", locale: ["en"])
        guard case .softUpdate(let prompt) = decision else { return XCTFail("\(decision)") }
        XCTAssertEqual(prompt.latestVersion?.description, "2.0")
        XCTAssertEqual(lookup.calls.with { $0 }, 1)
    }

    func testLookupIsNotAskedOnTestFlight() async {
        let lookup = CountingLookup(body: lookupBody)
        let decision = await checker(lookup: lookup).check(
            installedVersion: "1.4", osVersion: "17.0", locale: ["en"], channel: .testFlight, installedBuild: "8"
        )
        guard case .softUpdate(let prompt) = decision else { return XCTFail("\(decision)") }
        XCTAssertEqual(prompt.kind, .testBuild)
        XCTAssertEqual(prompt.latestBuild, SunsetBuildNumber("9"))
        XCTAssertEqual(lookup.calls.with { $0 }, 0)
    }

    func testSlowLookupIsCutOffAndTheConfigStillApplies() async {
        let start = Date()
        let decision = await checker(lookup: SlowLookup(), timeout: 0.2)
            .check(installedVersion: "1.3", osVersion: "17.0", locale: ["en"])
        XCTAssertLessThan(Date().timeIntervalSince(start), 2)
        guard case .softUpdate(let prompt) = decision else { return XCTFail("\(decision)") }
        XCTAssertEqual(prompt.latestVersion?.description, "1.4")
    }

    func testWithoutALookupTheConfigAloneApplies() async {
        let decision = await checker(lookup: nil).check(installedVersion: "1.4", osVersion: "17.0", locale: ["en"])
        XCTAssertEqual(decision, .upToDate)
    }

    func testLookupURLFromBundleID() {
        let source = URLSessionSunsetLookupSource(bundleID: "com.example.app", country: "de")
        XCTAssertEqual(source?.url.absoluteString, "https://itunes.apple.com/lookup?bundleId=com.example.app&country=de")
    }
}

/// A lookup that ignores its timeout and answers only after 3 seconds, without blocking a thread.
private struct SlowLookup: SunsetLookupSource {
    func fetch(timeout: TimeInterval) async -> Data? {
        await withCheckedContinuation { continuation in
            DispatchQueue.global().asyncAfter(deadline: .now() + 3) { continuation.resume(returning: nil) }
        }
    }
}
