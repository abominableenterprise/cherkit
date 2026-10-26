#if canImport(SwiftUI)
import CherKitSunset
import Foundation
import XCTest

/// Tests the soft prompt's "remember Not now" rule from CherKitUI.
///
/// Package.swift has no CherKitUI test target yet, so the two SwiftUI-free files (`SunsetSoftPromptRule.swift`,
/// `SunsetSoftPromptMemory.swift`) are compiled into this target through symlinks in this folder.
final class SunsetSoftPromptRuleTests: XCTestCase {
    private let prompt = SunsetPrompt(storeURL: URL(string: "https://apps.apple.com/app/id1")!, message: nil)
    private var defaults: UserDefaults!
    private let suiteName = "cherkit.sunset.tests.\(UUID().uuidString)"

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    func testSoftUpdateShowsWhenNeverDismissed() {
        XCTAssertTrue(SunsetSoftPromptRule.shouldShow(.softUpdate(prompt), version: "2.0", dismissedVersion: nil))
    }

    func testSoftUpdateHiddenForTheDismissedVersion() {
        XCTAssertFalse(SunsetSoftPromptRule.shouldShow(.softUpdate(prompt), version: "2.0", dismissedVersion: "2.0"))
    }

    func testSoftUpdateShowsAgainForANewerVersion() {
        XCTAssertTrue(SunsetSoftPromptRule.shouldShow(.softUpdate(prompt), version: "2.1", dismissedVersion: "2.0"))
    }

    func testOtherDecisionsNeverShowTheSoftPrompt() {
        for dismissed in [nil, "2.0"] {
            XCTAssertFalse(SunsetSoftPromptRule.shouldShow(nil, version: "2.0", dismissedVersion: dismissed))
            XCTAssertFalse(SunsetSoftPromptRule.shouldShow(.upToDate, version: "2.0", dismissedVersion: dismissed))
            XCTAssertFalse(SunsetSoftPromptRule.shouldShow(.hardUpdate(prompt), version: "2.0", dismissedVersion: dismissed))
        }
    }

    func testMemoryRemembersNotNowPerVersion() {
        let memory = SunsetSoftPromptMemory(defaults: defaults)
        XCTAssertNil(memory.dismissedVersion)
        XCTAssertTrue(memory.shouldShowSoftPrompt(for: .softUpdate(prompt), version: "2.0"))

        memory.rememberNotNow(for: "2.0")
        XCTAssertEqual(memory.dismissedVersion, "2.0")
        XCTAssertFalse(memory.shouldShowSoftPrompt(for: .softUpdate(prompt), version: "2.0"))
        XCTAssertTrue(memory.shouldShowSoftPrompt(for: .softUpdate(prompt), version: "2.1"))
    }

    func testMemorySurvivesANewInstanceLikeANextLaunch() {
        SunsetSoftPromptMemory(defaults: defaults).rememberNotNow(for: "2.0")
        let nextLaunch = SunsetSoftPromptMemory(defaults: defaults)
        XCTAssertFalse(nextLaunch.shouldShowSoftPrompt(for: .softUpdate(prompt), version: "2.0"))
    }

    func testMemoryUsesTheInjectedKey() {
        let memory = SunsetSoftPromptMemory(defaults: defaults, key: "custom.key")
        memory.rememberNotNow(for: "3.0")
        XCTAssertEqual(defaults.string(forKey: "custom.key"), "3.0")
        XCTAssertNil(defaults.string(forKey: SunsetSoftPromptMemory.defaultKey))
        XCTAssertNil(SunsetSoftPromptMemory(defaults: defaults).dismissedVersion)
    }

    func testResetShowsThePromptAgain() {
        let memory = SunsetSoftPromptMemory(defaults: defaults)
        memory.rememberNotNow(for: "2.0")
        memory.reset()
        XCTAssertNil(memory.dismissedVersion)
        XCTAssertTrue(memory.shouldShowSoftPrompt(for: .softUpdate(prompt), version: "2.0"))
    }
}
#endif
