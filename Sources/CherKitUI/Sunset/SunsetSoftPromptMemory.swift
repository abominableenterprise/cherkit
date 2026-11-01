#if canImport(SwiftUI)
import CherKitSunset
import Foundation

/// Remembers the user's answers to the soft prompt, so it does not come back for what they already answered:
///
/// - for a release prompt, the version they tapped "Not now" for (under ``key``);
/// - for a TestFlight test-build prompt, the build they answered, with "Not now" or "Update" (under ``testBuildKey``,
///   see ``SunsetTestBuildPromptRule``).
///
/// Stores one string per key in ``defaults``. Inject your own `UserDefaults` (an app group suite, or a throwaway
/// suite in tests and previews) or keys.
public struct SunsetSoftPromptMemory {
    /// The `UserDefaults` key used for the release prompt unless you pass another.
    public static let defaultKey = "cherkit.sunset.softPromptDismissedVersion"

    /// Where the answers are stored.
    public let defaults: UserDefaults
    /// The key the release prompt's "Not now" version is stored under.
    public let key: String
    /// The key the answered test build is stored under, ``SunsetTestBuildPromptRule/defaultMemoryKey`` by default.
    public let testBuildKey: String

    /// Creates a memory backed by `defaults` under `key` and `testBuildKey`.
    public init(
        defaults: UserDefaults = .standard,
        key: String = SunsetSoftPromptMemory.defaultKey,
        testBuildKey: String = SunsetTestBuildPromptRule.defaultMemoryKey
    ) {
        self.defaults = defaults
        self.key = key
        self.testBuildKey = testBuildKey
    }

    /// The version the user last tapped "Not now" for, or `nil` when never.
    public var dismissedVersion: String? {
        defaults.string(forKey: key)
    }

    /// The TestFlight build the user last answered ("Not now" or "Update"), or `nil` when never.
    public var rememberedTestBuild: String? {
        defaults.string(forKey: testBuildKey)
    }

    /// Records "Not now" for `version`. Any other version shows the prompt again.
    public func rememberNotNow(for version: String) {
        defaults.set(version, forKey: key)
    }

    /// Records an answer to `prompt`. A test-build prompt remembers its build for both answers
    /// (``SunsetTestBuildPromptRule/buildToRemember(after:on:)``); a release prompt remembers `version` for
    /// "Not now" only, so after "Update" it comes back until the app is updated.
    public func remember(_ action: SunsetPromptAction, on prompt: SunsetPrompt, version: String) {
        if prompt.kind == .testBuild {
            if let build = SunsetTestBuildPromptRule.buildToRemember(after: action, on: prompt) {
                defaults.set(build, forKey: testBuildKey)
            }
        } else if action == .notNow {
            rememberNotNow(for: version)
        }
    }

    /// Forgets every answer, release and test build, e.g. from a debug menu.
    public func reset() {
        defaults.removeObject(forKey: key)
        defaults.removeObject(forKey: testBuildKey)
    }

    /// Whether the soft prompt should appear for `decision` about `version`: ``SunsetTestBuildPromptRule`` for a
    /// test-build prompt (`version` is ignored), ``SunsetSoftPromptRule`` otherwise.
    public func shouldShowSoftPrompt(for decision: SunsetDecision?, version: String) -> Bool {
        if case .softUpdate(let prompt)? = decision, prompt.kind == .testBuild {
            return SunsetTestBuildPromptRule.shouldShow(decision, rememberedBuild: rememberedTestBuild)
        }
        return SunsetSoftPromptRule.shouldShow(decision, version: version, dismissedVersion: dismissedVersion)
    }
}
#endif
