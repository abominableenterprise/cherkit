#if canImport(SwiftUI)
import CherKitSunset
@testable import CherKitUI
import Foundation
import SwiftUI
import XCTest

/// `.sunsetPrompt(checker:onFirstCheckComplete:)`: runs once per process when the launch check's first attempt
/// resolves, always after the prompt callbacks heard that decision.
@available(iOS 16, macOS 13, *)
@MainActor
final class SunsetFirstCheckCompleteTests: XCTestCase {
    private static let hard = Data(#"{"schemaVersion": 1, "ios": {"minimumVersion": "2.0", "storeURL": "https://apps.apple.com/app/id1"}}"#.utf8)
    private static let soft = Data(#"{"schemaVersion": 1, "ios": {"latestVersion": "2.0", "storeURL": "https://apps.apple.com/app/id1"}}"#.utf8)
    private static let upToDate = Data(#"{"schemaVersion": 1, "ios": {"latestVersion": "1.0", "storeURL": "https://apps.apple.com/app/id1"}}"#.utf8)

    private let suiteName = "cherkit.sunset.first-check-tests.\(UUID().uuidString)"
    private var defaults: UserDefaults!
    /// Every callback, in order: `kind:soft`, `kind:hard`, `kind:nil`, `visible:true`, `visible:false`, `settled`.
    private var events: [String] = []

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suiteName)
        events = []
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    private func checker(_ source: SunsetConfigSource) -> SunsetChecker {
        SunsetChecker(source: source, cache: EmptySunsetCache())
    }

    /// The modifier `.sunsetPrompt(checker:)` builds, over `runner` instead of the shared one, recording to `events`.
    private func modifier(_ checker: SunsetChecker, runner: SunsetCheckRunner) -> SunsetCheckerPromptModifier {
        SunsetCheckerPromptModifier(
            request: SunsetCheckRunner.Request(checker: checker, installedVersion: "1.0", locale: ["en"]),
            minimumInterval: 0,
            memory: SunsetSoftPromptMemory(defaults: defaults),
            strings: SunsetStrings(),
            appIcon: nil,
            tint: nil,
            tintLabel: nil,
            onVisibilityChange: { [unowned self] in events.append("visible:\($0)") },
            onPromptChange: { [unowned self] in events.append("kind:\($0.map { "\($0)" } ?? "nil")") },
            onFirstCheckComplete: { [unowned self] in events.append("settled") },
            runner: runner
        )
    }

    /// Reports the runner's current state the way the view does: through a tracker that remembers the last report.
    private func render(_ modifier: SunsetCheckerPromptModifier, _ tracker: VisibilityTracker<SunsetPromptState>) {
        let prompt = modifier.promptModifier
        tracker.update(prompt.promptState, report: prompt.report(from:to:))
    }

    private func launch(_ source: SunsetConfigSource, runner: SunsetCheckRunner? = nil)
        async -> (SunsetCheckerPromptModifier, VisibilityTracker<SunsetPromptState>, SunsetCheckRunner) {
        let runner = runner ?? SunsetCheckRunner()
        let modifier = modifier(checker(source), runner: runner)
        let tracker = VisibilityTracker(hidden: SunsetPromptState())
        render(modifier, tracker)
        runner.launched(modifier.request)
        render(modifier, tracker)
        XCTAssertEqual(events, [], "nothing while the first fetch runs")
        await runner.task?.value
        render(modifier, tracker)
        return (modifier, tracker, runner)
    }

    func testHardBlockIsReportedBeforeSettled() async {
        _ = await launch(FirstCheckSource(body: Self.hard))
        XCTAssertEqual(events, ["kind:hard", "settled"])
    }

    func testSoftPromptIsReportedBeforeSettled() async {
        _ = await launch(FirstCheckSource(body: Self.soft))
        XCTAssertEqual(events, ["kind:soft", "visible:true", "settled"])
    }

    func testUpToDateSettlesWithNoPrompt() async {
        _ = await launch(FirstCheckSource(body: Self.upToDate))
        XCTAssertEqual(events, ["settled"])
    }

    func testFailedFetchStillSettlesAndTheQuickRetryDoesNotSettleAgain() async {
        let source = FirstCheckSource(body: nil)
        source.bodyAfterFirstFetch = Self.hard
        let (modifier, tracker, runner) = await launch(source, runner: SunsetCheckRunner(retryDelay: 0.01))
        XCTAssertEqual(events, ["settled"], "fails open, settled after the first attempt, not after the retry")
        XCTAssertNotNil(runner.retryTask, "precondition: the quick retry is waiting")

        await runner.retryTask?.value
        await runner.task?.value
        render(modifier, tracker)
        XCTAssertEqual(source.fetches, 2)
        XCTAssertEqual(events, ["settled", "kind:hard"])
    }

    func testRecheckOnReturnFromBackgroundDoesNotSettleAgain() async {
        let source = FirstCheckSource(body: Self.upToDate)
        let (modifier, tracker, runner) = await launch(source, runner: SunsetCheckRunner(minimumInterval: 0))
        source.body = Self.hard
        runner.scenePhaseChanged(to: .background, modifier.request)
        runner.scenePhaseChanged(to: .active, modifier.request)
        await runner.task?.value
        render(modifier, tracker)
        XCTAssertEqual(source.fetches, 2)
        XCTAssertEqual(events, ["settled", "kind:hard"])
    }

    func testFiresOnceEvenWhenTheViewIsRebuilt() async {
        let (_, _, runner) = await launch(FirstCheckSource(body: Self.hard))
        // The root view goes away and comes back: a new modifier and reporter over the same runner.
        let again = modifier(checker(FirstCheckSource(body: Self.hard)), runner: runner)
        let tracker = VisibilityTracker(hidden: SunsetPromptState())
        render(again, tracker)
        again.runner.launched(again.request)
        render(again, tracker)
        XCTAssertEqual(events, ["kind:hard", "settled", "kind:hard"], "the new view hears the kind, not settled again")
    }

    func testDisabledCheckerSettlesOnTheFirstAppear() {
        let runner = SunsetCheckRunner()
        let modifier = modifier(.disabled, runner: runner)
        let tracker = VisibilityTracker(hidden: SunsetPromptState())
        runner.launched(modifier.request)
        XCTAssertNil(runner.task, "no fetch to wait for")
        XCTAssertTrue(runner.firstCheckComplete)
        XCTAssertEqual(runner.decision, .upToDate)
        render(modifier, tracker)
        XCTAssertEqual(events, ["settled"])
    }

    func testDecisionFormNeverSettles() throws {
        var settled = 0
        let view: some View = Text("App").sunsetPrompt(decision: .constant(.upToDate))
        var prompt = try XCTUnwrap(view as? ModifiedContent<Text, SunsetPromptModifier>).modifier
        prompt.onFirstCheckComplete = { settled += 1 }
        XCTAssertFalse(prompt.promptState.settled)
        let tracker = VisibilityTracker(hidden: SunsetPromptState())
        tracker.update(prompt.promptState, report: prompt.report(from:to:))
        XCTAssertEqual(settled, 0)
    }
}

/// A source that counts its fetches and answers `body` (`nil`: a failed fetch).
private final class FirstCheckSource: SunsetConfigSource, @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    private var current: Data?
    private var later: Data?

    init(body: Data?) {
        current = body
    }

    var fetches: Int { lock.withLock { count } }

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

/// A cache that keeps nothing.
private struct EmptySunsetCache: SunsetConfigCache {
    func load() async -> Data? { nil }
    func save(_ data: Data) async {}
}
#endif
