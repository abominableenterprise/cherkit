#if canImport(SwiftUI)
import CherKitSunset
import Foundation

/// Remembers the version the user tapped "Not now" for, so the soft prompt is not shown again for that version.
///
/// Stores one string under ``key`` in ``defaults``. Inject your own `UserDefaults` (an app group suite, or a throwaway
/// suite in tests and previews) or key.
public struct SunsetSoftPromptMemory {
    /// The `UserDefaults` key used unless you pass another.
    public static let defaultKey = "cherkit.sunset.softPromptDismissedVersion"

    /// Where the dismissed version is stored.
    public let defaults: UserDefaults
    /// The key it is stored under.
    public let key: String

    /// Creates a memory backed by `defaults` under `key`.
    public init(defaults: UserDefaults = .standard, key: String = SunsetSoftPromptMemory.defaultKey) {
        self.defaults = defaults
        self.key = key
    }

    /// The version the user last tapped "Not now" for, or `nil` when never.
    public var dismissedVersion: String? {
        defaults.string(forKey: key)
    }

    /// Records "Not now" for `version`. Any other version shows the prompt again.
    public func rememberNotNow(for version: String) {
        defaults.set(version, forKey: key)
    }

    /// Forgets any "Not now", e.g. from a debug menu.
    public func reset() {
        defaults.removeObject(forKey: key)
    }

    /// Whether the soft prompt should appear for `decision` about `version`. See ``SunsetSoftPromptRule``.
    public func shouldShowSoftPrompt(for decision: SunsetDecision?, version: String) -> Bool {
        SunsetSoftPromptRule.shouldShow(decision, version: version, dismissedVersion: dismissedVersion)
    }
}
#endif
