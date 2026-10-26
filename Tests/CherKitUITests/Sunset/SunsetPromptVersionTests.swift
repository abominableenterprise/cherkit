#if canImport(SwiftUI)
import CherKitCore
import CherKitSunset
@testable import CherKitUI
import SwiftUI
import XCTest

/// Which version `.sunsetPrompt` remembers "Not now" for: the parameter, else the prompt's `latestVersion`, else the
/// installed version.
@available(iOS 16, macOS 13, *)
final class SunsetPromptVersionTests: XCTestCase {
    private let storeURL = URL(string: "https://apps.apple.com/app/id1")!
    private var defaults: UserDefaults!
    private let suiteName = "cherkit.sunset.version-tests.\(UUID().uuidString)"

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    /// The modifier `.sunsetPrompt(...)` attaches, read back through the public API.
    private func modifier(_ decision: SunsetDecision, latestVersion: String? = nil) throws -> SunsetPromptModifier {
        let memory = SunsetSoftPromptMemory(defaults: defaults)
        let view: some View = Text("App").sunsetPrompt(decision: .constant(decision), latestVersion: latestVersion, memory: memory)
        return try XCTUnwrap(view as? ModifiedContent<Text, SunsetPromptModifier>).modifier
    }

    func testNotNowKeysOnThePromptsLatestVersionWhenNoParameterIsPassed() throws {
        let prompt = SunsetPrompt(storeURL: storeURL, message: nil, latestVersion: AppVersion("1.4.0"))
        let modifier = try modifier(.softUpdate(prompt))
        XCTAssertNil(modifier.latestVersion)
        XCTAssertTrue(modifier.showsSoftPrompt)

        modifier.rememberNotNow(for: prompt)

        XCTAssertEqual(SunsetSoftPromptMemory(defaults: defaults).dismissedVersion, "1.4.0")
        XCTAssertFalse(modifier.showsSoftPrompt)
        // A newer latestVersion in the config shows the prompt again.
        let newer = SunsetPrompt(storeURL: storeURL, message: nil, latestVersion: AppVersion("1.5"))
        XCTAssertTrue(try self.modifier(.softUpdate(newer)).showsSoftPrompt)
    }

    func testTheParameterStillWinsOverThePrompt() throws {
        let prompt = SunsetPrompt(storeURL: storeURL, message: nil, latestVersion: AppVersion("1.4.0"))
        let modifier = try modifier(.softUpdate(prompt), latestVersion: "9.9")
        modifier.rememberNotNow(for: prompt)
        XCTAssertEqual(SunsetSoftPromptMemory(defaults: defaults).dismissedVersion, "9.9")
    }

    func testWithoutEitherTheInstalledVersionIsUsed() throws {
        let prompt = SunsetPrompt(storeURL: storeURL, message: nil)
        let modifier = try modifier(.softUpdate(prompt))
        XCTAssertEqual(modifier.version(for: prompt), modifier.installedVersion)
        modifier.rememberNotNow(for: prompt)
        XCTAssertEqual(SunsetSoftPromptMemory(defaults: defaults).dismissedVersion, modifier.installedVersion)
    }
}
#endif
