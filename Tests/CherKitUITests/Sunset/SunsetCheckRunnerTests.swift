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

    func testDisabledCheckerAnswersUpToDate() async {
        let runner = SunsetCheckRunner()
        runner.launched(request(.disabled))
        await finish(runner)
        XCTAssertEqual(runner.decision, .upToDate)
    }
}

/// A source that counts its fetches.
private final class CountingSunsetSource: SunsetConfigSource, @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    let body: Data

    init(body: Data) {
        self.body = body
    }

    var fetches: Int {
        lock.withLock { count }
    }

    func fetch(timeout: TimeInterval) async -> Data? {
        lock.withLock { count += 1 }
        return body
    }
}

/// A cache that keeps nothing.
private struct NoSunsetCache: SunsetConfigCache {
    func load() async -> Data? { nil }
    func save(_ data: Data) async {}
}
#endif
