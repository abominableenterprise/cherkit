#if canImport(SwiftUI)
import CherKitSunset
@testable import CherKitUI
import Foundation
import XCTest

/// The runner behind `.sunsetPrompt(checker:)` turns the scheduler's answers into checker calls and keeps the decision.
@MainActor
final class SunsetCheckRunnerTests: XCTestCase {
    private static let hard = Data(#"{"schemaVersion": 1, "ios": {"minimumVersion": "2.0", "storeURL": "https://apps.apple.com/app/id1"}}"#.utf8)

    private func request(_ checker: SunsetChecker) -> SunsetCheckRunner.Request {
        SunsetCheckRunner.Request(checker: checker, installedVersion: "1.0", locale: ["en"])
    }

    private func finish(_ runner: SunsetCheckRunner) async {
        await runner.task?.value
    }

    func testLaunchCheckSetsTheDecisionOnce() async {
        let source = CountingSunsetSource(body: Self.hard)
        let runner = SunsetCheckRunner()
        let request = request(SunsetChecker(source: source, cache: NoSunsetCache()))

        runner.launched(request)
        runner.scenePhaseChanged(to: .active, request)
        await finish(runner)
        runner.launched(request)
        await finish(runner)

        XCTAssertEqual(source.fetches, 1)
        guard case .hardUpdate = runner.decision else { return XCTFail("expected the hard block, got \(String(describing: runner.decision))") }
        XCTAssertNil(runner.task)
        XCTAssertFalse(runner.scheduler.isChecking)
    }

    func testReturnFromBackgroundChecksAgain() async {
        let source = CountingSunsetSource(body: Self.hard)
        let runner = SunsetCheckRunner(minimumInterval: 0)
        let request = request(SunsetChecker(source: source, cache: NoSunsetCache()))
        runner.launched(request)
        await finish(runner)

        runner.scenePhaseChanged(to: .background, request)
        runner.scenePhaseChanged(to: .inactive, request)
        runner.scenePhaseChanged(to: .active, request)
        await finish(runner)

        XCTAssertEqual(source.fetches, 2)
    }

    /// Waits for the running check, then for the quick retry and the check it starts, if any.
    private func settle(_ runner: SunsetCheckRunner) async {
        await finish(runner)
        await runner.retryTask?.value
        await finish(runner)
    }

    func testFailedLaunchCheckIsRetriedOnceThenTheNextForegroundRechecks() async {
        let source = CountingSunsetSource(body: nil)
        let runner = SunsetCheckRunner(minimumInterval: 3600, retryDelay: 0.01)
        let request = request(SunsetChecker(source: source, cache: NoSunsetCache()))

        runner.launched(request)
        await settle(runner)
        XCTAssertEqual(source.fetches, 2, "the launch check and one quick retry")
        XCTAssertNil(runner.retryTask, "no second retry")
        XCTAssertEqual(runner.decision, .upToDate, "fails open meanwhile")

        // The network comes back; the next real return from the background re-checks, well within the hour.
        source.body = Self.hard
        runner.scenePhaseChanged(to: .background, request)
        runner.scenePhaseChanged(to: .inactive, request)
        runner.scenePhaseChanged(to: .active, request)
        await settle(runner)
        XCTAssertEqual(source.fetches, 3)
        guard case .hardUpdate = runner.decision else { return XCTFail("expected the hard block, got \(String(describing: runner.decision))") }

        // That check succeeded, so the interval applies again.
        runner.scenePhaseChanged(to: .background, request)
        runner.scenePhaseChanged(to: .active, request)
        await settle(runner)
        XCTAssertEqual(source.fetches, 3)
    }

    func testQuickRetryShowsTheHardBlockWhenTheFetchRecovers() async {
        let source = CountingSunsetSource(body: nil)
        source.bodyAfterFirstFetch = Self.hard
        let runner = SunsetCheckRunner(retryDelay: 0.01)
        runner.launched(request(SunsetChecker(source: source, cache: NoSunsetCache())))
        await settle(runner)
        XCTAssertEqual(source.fetches, 2)
        guard case .hardUpdate = runner.decision else { return XCTFail("expected the hard block, got \(String(describing: runner.decision))") }
        XCTAssertEqual(runner.scheduler.failuresInARow, 0)
    }

    func testFailedFetchWithAUsableCacheIsNotRetried() async {
        let source = CountingSunsetSource(body: nil)
        let runner = SunsetCheckRunner(retryDelay: 0.01)
        runner.launched(request(SunsetChecker(source: source, cache: FixedSunsetCache(body: Self.hard))))
        await settle(runner)
        XCTAssertEqual(source.fetches, 1)
        XCTAssertNil(runner.retryTask)
        guard case .hardUpdate = runner.decision else { return XCTFail("expected the cached hard block, got \(String(describing: runner.decision))") }
    }

    func testDefaultRetryDelayIsAFewSeconds() {
        XCTAssertEqual(SunsetCheckRunner().retryDelay, 3)
    }

    func testDisabledCheckerAnswersUpToDate() async {
        let runner = SunsetCheckRunner()
        runner.launched(request(.disabled))
        await finish(runner)
        XCTAssertEqual(runner.decision, .upToDate)
    }
}

/// A source that counts its fetches and answers `body` (`nil`: a failed fetch).
private final class CountingSunsetSource: SunsetConfigSource, @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    private var current: Data?
    private var later: Data?

    init(body: Data?) {
        current = body
    }

    var fetches: Int {
        lock.withLock { count }
    }

    /// What the next fetches answer.
    var body: Data? {
        get { lock.withLock { current } }
        set { lock.withLock { current = newValue } }
    }

    /// When set, what every fetch after the first answers.
    var bodyAfterFirstFetch: Data? {
        get { lock.withLock { later } }
        set { lock.withLock { later = newValue } }
    }

    func fetch(timeout: TimeInterval) async -> Data? {
        lock.withLock {
            count += 1
            if count > 1, let later { return later }
            return current
        }
    }
}

/// A cache holding a fixed config.
private struct FixedSunsetCache: SunsetConfigCache {
    let body: Data
    func load() async -> Data? { body }
    func save(_ data: Data) async {}
}

/// A cache that keeps nothing.
private struct NoSunsetCache: SunsetConfigCache {
    func load() async -> Data? { nil }
    func save(_ data: Data) async {}
}
#endif
