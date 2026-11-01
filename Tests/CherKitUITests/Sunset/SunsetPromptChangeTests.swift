#if canImport(SwiftUI)
import CherKitCore
import CherKitSunset
@testable import CherKitUI
import SwiftUI
import XCTest

/// What `.sunsetPrompt(onVisibilityChange:onPromptChange:)` reports: which prompt is showing, and the soft alert's
/// visibility on its own.
@available(iOS 16, macOS 13, *)
@MainActor
final class SunsetPromptChangeTests: XCTestCase {
    private let suiteName = "cherkit.sunset.change-tests.\(UUID().uuidString)"
    private var defaults: UserDefaults!
    private let prompt = SunsetPrompt(
        storeURL: URL(string: "https://apps.apple.com/app/id1")!, message: nil, latestVersion: AppVersion("2.0.0"))

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    private func modifier(
        _ decision: SunsetDecision?,
        visibility: ((Bool) -> Void)? = nil,
        kinds: ((SunsetPromptKind?) -> Void)? = nil
    ) throws -> SunsetPromptModifier {
        let view: some View = Text("App").sunsetPrompt(
            decision: .constant(decision), memory: SunsetSoftPromptMemory(defaults: defaults),
            onVisibilityChange: visibility, onPromptChange: kinds)
        return try XCTUnwrap(view as? ModifiedContent<Text, SunsetPromptModifier>).modifier
    }

    func testPromptKindFollowsTheDecision() throws {
        XCTAssertEqual(try modifier(.softUpdate(prompt)).promptKind, .soft)
        XCTAssertEqual(try modifier(.hardUpdate(prompt)).promptKind, .hard)
        XCTAssertNil(try modifier(.upToDate).promptKind)
        XCTAssertNil(try modifier(nil).promptKind)
    }

    func testSoftPromptAnsweredNotNowIsNoLongerReported() throws {
        let soft = try modifier(.softUpdate(prompt))
        soft.rememberNotNow(for: prompt)
        XCTAssertNil(soft.promptKind)
    }

    func testOnVisibilityChangeHearsOnlyTheSoftAlert() throws {
        var visibility: [Bool] = []
        var kinds: [SunsetPromptKind?] = []
        let modifier = try modifier(nil, visibility: { visibility.append($0) }, kinds: { kinds.append($0) })
        modifier.report(from: nil, to: .soft)
        modifier.report(from: .soft, to: .hard)
        modifier.report(from: .hard, to: nil)
        modifier.report(from: nil, to: .hard)
        XCTAssertEqual(kinds, [.soft, .hard, nil, .hard])
        XCTAssertEqual(visibility, [true, false], "the hard block is not reported as the soft alert")
    }

    func testCallbacksAreOptional() throws {
        try modifier(nil).report(from: nil, to: .soft)
    }
}
#endif
