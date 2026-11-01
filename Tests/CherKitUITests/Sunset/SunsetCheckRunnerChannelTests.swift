#if canImport(SwiftUI)
import CherKitSunset
@testable import CherKitUI
import Foundation
import SwiftUI
import XCTest

/// The runner behind `.sunsetPrompt(checker:channel:)`: passes the channel and build to the checker, and shows at
/// most one soft prompt per session.
@available(iOS 16, macOS 13, *)
@MainActor
final class SunsetCheckRunnerChannelTests: XCTestCase {
    private static let config = Data(#"""
        {"schemaVersion": 1, "ios": {"latestVersion": "1.5", "storeURL": "https://apps.apple.com/app/id1",
         "testFlight": {"latestBuild": "42", "url": "itms-beta://beta.itunes.apple.com/v1/app/1"}}}
        """#.utf8)
    private static let hard = Data(#"{"schemaVersion": 1, "ios": {"minimumVersion": "2.0", "storeURL": "https://apps.apple.com/app/id1"}}"#.utf8)

    private func request(_ source: SunsetConfigSource, channel: SunsetChannel = .store, build: String? = "41") -> SunsetCheckRunner.Request {
        SunsetCheckRunner.Request(
            checker: SunsetChecker(source: source, cache: EmptyCache()),
            installedVersion: "1.0",
            locale: ["en"],
            channel: channel,
            installedBuild: build
        )
    }

    private func check(_ runner: SunsetCheckRunner, _ request: SunsetCheckRunner.Request) async {
        runner.launched(request)
        await runner.task?.value
    }

    private func recheck(_ runner: SunsetCheckRunner, _ request: SunsetCheckRunner.Request) async {
        runner.scenePhaseChanged(to: .background, request)
        runner.scenePhaseChanged(to: .active, request)
        await runner.task?.value
    }

    func testTestFlightChannelGetsTheTestBuildPrompt() async {
        let runner = SunsetCheckRunner()
        await check(runner, request(Body(Self.config), channel: .testFlight))
        guard case .softUpdate(let prompt)? = runner.decision else { return XCTFail("\(String(describing: runner.decision))") }
        XCTAssertEqual(prompt.kind, .testBuild)
        XCTAssertEqual(prompt.latestBuild?.description, "42")
        XCTAssertEqual(prompt.storeURL.absoluteString, "itms-beta://beta.itunes.apple.com/v1/app/1")
    }

    func testDefaultStoreChannelKeepsTheReleasePrompt() async {
        let runner = SunsetCheckRunner()
        await check(runner, SunsetCheckRunner.Request(
            checker: SunsetChecker(source: Body(Self.config), cache: EmptyCache()), installedVersion: "1.0", locale: ["en"]))
        guard case .softUpdate(let prompt)? = runner.decision else { return XCTFail("\(String(describing: runner.decision))") }
        XCTAssertEqual(prompt.kind, .update)
        XCTAssertEqual(prompt.latestVersion?.description, "1.5")
    }

    func testOtherChannelGetsNoTestBuildPrompt() async {
        let runner = SunsetCheckRunner()
        await check(runner, request(Body(Self.config), channel: .other))
        guard case .softUpdate(let prompt)? = runner.decision else { return XCTFail("\(String(describing: runner.decision))") }
        XCTAssertEqual(prompt.kind, .update, "only the config's release version applies")
    }

    func testAtMostOneSoftPromptPerSession() async {
        let source = Body(Self.config)
        let runner = SunsetCheckRunner(minimumInterval: 0)
        let request = request(source, channel: .testFlight)
        await check(runner, request)
        guard case .softUpdate? = runner.decision else { return XCTFail("the launch check shows the prompt") }

        runner.softPromptAnswered()
        runner.decision = nil
        await recheck(runner, request)
        XCTAssertEqual(runner.decision, .upToDate, "answered once this session: no second soft prompt")

        source.body = Self.hard
        await recheck(runner, request)
        guard case .hardUpdate? = runner.decision else { return XCTFail("a hard block still shows") }
    }

    func testUnansweredSoftPromptShowsAgainOnARecheck() async {
        let runner = SunsetCheckRunner(minimumInterval: 0)
        let request = request(Body(Self.config), channel: .testFlight)
        await check(runner, request)
        await recheck(runner, request)
        guard case .softUpdate? = runner.decision else { return XCTFail("\(String(describing: runner.decision))") }
    }

    func testModifierPassesTheChannelAndTheInstalledBuild() throws {
        let view = Text("App").sunsetPrompt(checker: .disabled, channel: .testFlight)
        let modifier = try XCTUnwrap(view as? ModifiedContent<Text, SunsetCheckerPromptModifier>).modifier
        XCTAssertEqual(modifier.request.channel, .testFlight)
        XCTAssertEqual(modifier.request.installedBuild, SunsetChecker.installedBuild())
        let plain = try XCTUnwrap(Text("App").sunsetPrompt(checker: .disabled) as? ModifiedContent<Text, SunsetCheckerPromptModifier>)
        XCTAssertEqual(plain.modifier.request.channel, .store, "the default keeps the behaviour from before channels")
    }
}

/// A source answering `body`, changeable between checks.
private final class Body: SunsetConfigSource, @unchecked Sendable {
    private let lock = NSLock()
    private var current: Data?

    init(_ body: Data?) {
        current = body
    }

    var body: Data? {
        get { lock.withLock { current } }
        set { lock.withLock { current = newValue } }
    }

    func fetch(timeout: TimeInterval) async -> Data? {
        body
    }
}

/// A cache that keeps nothing.
private struct EmptyCache: SunsetConfigCache {
    func load() async -> Data? { nil }
    func save(_ data: Data) async {}
}
#endif
