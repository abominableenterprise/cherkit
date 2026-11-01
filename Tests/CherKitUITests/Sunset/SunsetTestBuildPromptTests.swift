#if canImport(SwiftUI)
import CherKitCore
import CherKitSunset
@testable import CherKitUI
import SwiftUI
import XCTest

/// `.sunsetPrompt` for a TestFlight test-build prompt: its copy, its "Update" target, the per-build memory
/// (`SunsetTestBuildPromptRule`), and that callbacks hear it as `.soft`.
@available(iOS 16, macOS 13, *)
@MainActor
final class SunsetTestBuildPromptTests: XCTestCase {
    private let testFlightURL = URL(string: "itms-beta://beta.itunes.apple.com/v1/app/123")!
    private let suiteName = "cherkit.sunset.test-build-tests.\(UUID().uuidString)"
    private var defaults: UserDefaults!

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    private func testBuild(_ build: String, message: String? = nil) -> SunsetPrompt {
        SunsetPrompt(storeURL: testFlightURL, message: message, kind: .testBuild, latestBuild: SunsetBuildNumber(build))
    }

    private var memory: SunsetSoftPromptMemory { SunsetSoftPromptMemory(defaults: defaults) }

    private func modifier(_ decision: SunsetDecision?, kinds: ((SunsetPromptKind?) -> Void)? = nil) throws -> SunsetPromptModifier {
        let view: some View = Text("App").sunsetPrompt(decision: .constant(decision), memory: memory, onPromptChange: kinds)
        return try XCTUnwrap(view as? ModifiedContent<Text, SunsetPromptModifier>).modifier
    }

    // MARK: Copy

    func testTestBuildCopy() {
        let strings = SunsetStrings()
        let prompt = testBuild("42")
        XCTAssertEqual(strings.softTitle(for: prompt), "A new test build is available")
        XCTAssertEqual(strings.softText(for: prompt), strings.testBuildMessage)
        XCTAssertEqual(strings.softUpdateHint(for: prompt), strings.testBuildUpdateHint)
        XCTAssertEqual(strings.updateButton, "Update")
    }

    func testReleasePromptKeepsTheUpdateAvailableCopy() {
        let strings = SunsetStrings()
        let prompt = SunsetPrompt(storeURL: URL(string: "https://apps.apple.com/app/id1")!, message: nil, latestVersion: AppVersion("2.0"))
        XCTAssertEqual(strings.softTitle(for: prompt), "Update available")
        XCTAssertEqual(strings.softTitle(for: nil), "Update available")
        XCTAssertEqual(strings.softText(for: prompt), strings.softMessage)
        XCTAssertEqual(strings.softUpdateHint(for: prompt), strings.updateHint)
    }

    func testTestBuildCopyIsLocalizableAndTheConfigMessageWins() {
        let strings = SunsetStrings(testBuildTitle: "Neuer Testbuild", testBuildMessage: "Jetzt testen")
        XCTAssertEqual(strings.softTitle(for: testBuild("42")), "Neuer Testbuild")
        XCTAssertEqual(strings.softText(for: testBuild("42")), "Jetzt testen")
        XCTAssertEqual(strings.softText(for: testBuild("42", message: "Build 42: new sync")), "Build 42: new sync")
    }

    // MARK: Update button

    func testUpdateOpensTheTestFlightURLAndRemembersTheBuild() throws {
        let prompt = testBuild("42")
        let modifier = try modifier(.softUpdate(prompt))
        XCTAssertTrue(modifier.showsSoftPrompt)

        var opened: [URL] = []
        modifier.update(prompt) { opened.append($0) }

        XCTAssertEqual(opened, [testFlightURL])
        XCTAssertEqual(defaults.string(forKey: SunsetTestBuildPromptRule.defaultMemoryKey), "42")
        XCTAssertFalse(modifier.showsSoftPrompt, "Update counts as answered for that build")
        XCTAssertNil(memory.dismissedVersion, "the release prompt's memory is untouched")
    }

    // MARK: Per-build memory

    func testNotNowRemembersTheBuildUnderItsOwnKey() throws {
        let prompt = testBuild("42")
        let modifier = try modifier(.softUpdate(prompt))
        modifier.rememberNotNow(for: prompt)

        XCTAssertEqual(memory.rememberedTestBuild, "42")
        XCTAssertNil(memory.dismissedVersion)
        XCTAssertFalse(modifier.showsSoftPrompt)
    }

    func testEachBuildPromptsAtMostOnce() throws {
        let first = try modifier(.softUpdate(testBuild("42")))
        first.rememberNotNow(for: testBuild("42"))
        XCTAssertFalse(try modifier(.softUpdate(testBuild("42"))).showsSoftPrompt, "same build, next launch")
        XCTAssertFalse(try modifier(.softUpdate(testBuild("42.0"))).showsSoftPrompt, "builds compare as numbers")
        XCTAssertTrue(try modifier(.softUpdate(testBuild("43"))).showsSoftPrompt, "a new build prompts again")
    }

    func testReleaseNotNowDoesNotHideATestBuild() throws {
        memory.rememberNotNow(for: "")
        memory.rememberNotNow(for: "1.0")
        XCTAssertTrue(try modifier(.softUpdate(testBuild("42"))).showsSoftPrompt)
    }

    func testReleaseUpdateIsNotRemembered() throws {
        let prompt = SunsetPrompt(storeURL: URL(string: "https://apps.apple.com/app/id1")!, message: nil, latestVersion: AppVersion("2.0"))
        let modifier = try modifier(.softUpdate(prompt))
        var opened: [URL] = []
        modifier.update(prompt) { opened.append($0) }
        XCTAssertEqual(opened, [prompt.storeURL])
        XCTAssertNil(memory.dismissedVersion, "Update on a release prompt keeps asking until the app is updated")
        XCTAssertNil(memory.rememberedTestBuild)
        XCTAssertTrue(modifier.showsSoftPrompt)
    }

    func testResetForgetsTheTestBuildToo() {
        memory.remember(.update, on: testBuild("42"), version: "1.0")
        memory.rememberNotNow(for: "2.0")
        memory.reset()
        XCTAssertNil(memory.rememberedTestBuild)
        XCTAssertNil(memory.dismissedVersion)
    }

    func testCustomTestBuildKey() {
        let custom = SunsetSoftPromptMemory(defaults: defaults, testBuildKey: "custom.build")
        custom.remember(.notNow, on: testBuild("7"), version: "1.0")
        XCTAssertEqual(defaults.string(forKey: "custom.build"), "7")
        XCTAssertNil(defaults.string(forKey: SunsetTestBuildPromptRule.defaultMemoryKey))
    }

    // MARK: Callbacks

    func testTestBuildPromptIsReportedAsSoft() throws {
        var kinds: [SunsetPromptKind?] = []
        let modifier = try modifier(.softUpdate(testBuild("42")), kinds: { kinds.append($0) })
        XCTAssertEqual(modifier.promptKind, .soft)
        modifier.report(from: SunsetPromptState(), to: modifier.promptState)
        XCTAssertEqual(kinds, [.soft])

        modifier.rememberNotNow(for: testBuild("42"))
        XCTAssertNil(modifier.promptKind, "answered: nothing showing")
    }

    func testAnsweringCallsTheSessionHook() throws {
        var answered = 0
        var modifier = try modifier(.softUpdate(testBuild("42")))
        modifier.onSoftPromptAnswered = { answered += 1 }
        modifier.update(testBuild("42")) { _ in }
        modifier.rememberNotNow(for: testBuild("42"))
        XCTAssertEqual(answered, 2)
    }
}
#endif
