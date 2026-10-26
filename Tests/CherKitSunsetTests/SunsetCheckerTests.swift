@testable import CherKitSunset
import Foundation
import XCTest
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// The platform adapters and the checker's deadline. The fixture cases live in `SunsetCheckerFixtureTests`.
final class SunsetCheckerTests: XCTestCase {
    private let hard = Data(#"{"schemaVersion": 1, "ios": {"minimumVersion": "2.0", "storeURL": "https://apps.apple.com/app/id1"}}"#.utf8)
    private let soft = Data(#"{"schemaVersion": 1, "ios": {"latestVersion": "2.0", "storeURL": "https://apps.apple.com/app/id2"}}"#.utf8)

    func testSourceThatIgnoresItsTimeoutIsCutOffAndTheCacheIsUsed() async {
        let cache = MemorySunsetCache(soft)
        let checker = SunsetChecker(source: SlowSunsetSource(body: hard, delay: 5), cache: cache, timeout: 0.2)
        let start = Date()
        let decision = await checker.check(installedVersion: "1.0", osVersion: "17.0", locale: ["en"])
        XCTAssertLessThan(Date().timeIntervalSince(start), 2)
        XCTAssertEqual(decision.prompt?.storeURL.absoluteString, "https://apps.apple.com/app/id2")
        guard case .softUpdate = decision else { return XCTFail("expected the cached soft prompt, got \(decision)") }
        XCTAssertEqual(cache.saved, [])
    }

    func testSourceWithinTheTimeoutIsUsed() async {
        let cache = MemorySunsetCache(soft)
        let checker = SunsetChecker(source: SlowSunsetSource(body: hard, delay: 0.05), cache: cache, timeout: 5)
        let decision = await checker.check(installedVersion: "1.0", osVersion: "17.0", locale: ["en"])
        guard case .hardUpdate = decision else { return XCTFail("expected the fresh hard block, got \(decision)") }
        XCTAssertEqual(cache.saved, [hard])
    }

    func testNonPositiveTimeoutFailsOpenAtOnce() async {
        for timeout in [0, -1, .nan] as [TimeInterval] {
            let checker = SunsetChecker(source: SlowSunsetSource(body: hard, delay: 5), cache: MemorySunsetCache(), timeout: timeout)
            let decision = await checker.check(installedVersion: "1.0", osVersion: "17.0", locale: [])
            XCTAssertEqual(decision, .upToDate, "\(timeout)")
        }
        XCTAssertEqual(SunsetTimeout.nanoseconds(.infinity), UInt64(SunsetTimeout.maximumSeconds * 1_000_000_000))
    }

    func testDefaults() {
        let checker = SunsetChecker(url: URL(string: "https://example.com/sunset.json")!)
        XCTAssertEqual(checker.timeout, 5)
        XCTAssertEqual(checker.platform, .ios)
        XCTAssertEqual((checker.cache as? UserDefaultsSunsetConfigCache)?.key, "cherkit.sunset.config")
        XCTAssertEqual((checker.source as? URLSessionSunsetConfigSource)?.url.absoluteString, "https://example.com/sunset.json")
    }

    func testURLSessionSourceIgnoresTheHTTPCacheAndUsesTheTimeout() async throws {
        let url = StubURLProtocol.serve(.response(status: 200, body: hard))
        let fetched = await URLSessionSunsetConfigSource(url: url, session: StubURLProtocol.session).fetch(timeout: 3)
        XCTAssertEqual(fetched, hard)
        let request = try XCTUnwrap(StubURLProtocol.request(for: url))
        XCTAssertEqual(request.cachePolicy, .reloadIgnoringLocalAndRemoteCacheData)
        XCTAssertEqual(request.timeoutInterval, 3)
    }

    func testOnlyNonEmpty2xxHTTPBodiesCount() throws {
        let url = URL(string: "https://example.com/sunset.json")!
        func http(_ status: Int) throws -> URLResponse {
            try XCTUnwrap(HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil))
        }
        XCTAssertEqual(URLSessionSunsetConfigSource.body(hard, response: try http(200)), hard)
        XCTAssertEqual(URLSessionSunsetConfigSource.body(hard, response: try http(299)), hard)
        for status in [100, 199, 300, 304, 404, 500] {
            XCTAssertNil(URLSessionSunsetConfigSource.body(hard, response: try http(status)), "\(status)")
        }
        XCTAssertNil(URLSessionSunsetConfigSource.body(Data(), response: try http(200)))
        let notHTTP = URLResponse(url: url, mimeType: "application/json", expectedContentLength: 1, textEncodingName: nil)
        XCTAssertNil(URLSessionSunsetConfigSource.body(hard, response: notHTTP))
    }

    func testUserDefaultsCacheRoundTripsUnderItsKey() async throws {
        let suite = "cherkit.sunset.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let cache = UserDefaultsSunsetConfigCache(defaults: defaults, key: "test.config")
        let empty = await cache.load()
        XCTAssertNil(empty)

        await cache.save(soft)
        let loaded = await cache.load()
        XCTAssertEqual(loaded, soft)
        XCTAssertEqual(defaults.data(forKey: "test.config"), soft)
        let otherKey = await UserDefaultsSunsetConfigCache(defaults: defaults).load()
        XCTAssertNil(otherKey)

        defaults.set(42, forKey: "test.config")
        let wrongType = await cache.load()
        XCTAssertNil(wrongType)
    }

    func testMalformedFetchIsNeverWrittenToUserDefaults() async throws {
        let suite = "cherkit.sunset.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let cache = UserDefaultsSunsetConfigCache(defaults: defaults)
        await cache.save(soft)
        let source = URLSessionSunsetConfigSource(
            url: StubURLProtocol.serve(.response(status: 200, body: Data("<html>oops</html>".utf8))),
            session: StubURLProtocol.session)

        let decision = await SunsetChecker(source: source, cache: cache).check(installedVersion: "1.0", osVersion: "17", locale: [])
        guard case .softUpdate = decision else { return XCTFail("expected the cached soft prompt, got \(decision)") }
        XCTAssertEqual(defaults.data(forKey: UserDefaultsSunsetConfigCache.defaultKey), soft)
    }

    func testInstalledVersionReadsShortVersionString() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let dir = root.appendingPathComponent("App.bundle"), bareDir = root.appendingPathComponent("Bare.bundle")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: bareDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        XCTAssertEqual(SunsetChecker.installedVersion(bundle: try XCTUnwrap(Bundle(path: bareDir.path))), "")

        let plist = try PropertyListSerialization.data(
            fromPropertyList: ["CFBundleShortVersionString": "1.4.0", "CFBundleIdentifier": "test.sunset"],
            format: .xml, options: 0)
        try plist.write(to: dir.appendingPathComponent("Info.plist"))
        XCTAssertEqual(SunsetChecker.installedVersion(bundle: try XCTUnwrap(Bundle(path: dir.path))), "1.4.0")
    }

    func testOSVersionIsMajorMinorPatch() {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        XCTAssertEqual(SunsetChecker.osVersion(), "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)")
    }
}
