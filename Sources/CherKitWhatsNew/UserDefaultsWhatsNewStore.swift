import Foundation

/// ``WhatsNewStore`` in `UserDefaults`: the last-seen version as a string and the install history as an array of
/// strings, under `<keyPrefix>lastSeenVersion` and `<keyPrefix>installHistory`. A stored value of the wrong type
/// counts as absent, so a corrupt store behaves like a first launch instead of failing.
public struct UserDefaultsWhatsNewStore: WhatsNewStore, @unchecked Sendable {
    /// Default prefix of the keys.
    public static let defaultKeyPrefix = "cherkit.whatsnew."

    // UserDefaults is documented as thread-safe.
    private let defaults: UserDefaults
    private let keyPrefix: String

    /// A store in `defaults` (e.g. `UserDefaults(suiteName: "group.com.example")`) with keys starting `keyPrefix`.
    public init(defaults: UserDefaults = .standard, keyPrefix: String = defaultKeyPrefix) {
        self.defaults = defaults
        self.keyPrefix = keyPrefix
    }

    private var lastSeenKey: String { keyPrefix + "lastSeenVersion" }
    private var historyKey: String { keyPrefix + "installHistory" }

    /// The stored last-seen version, or `nil` when absent or not a string.
    public func lastSeenVersion() -> String? { defaults.object(forKey: lastSeenKey) as? String }

    /// Stores the last-seen version.
    public func setLastSeenVersion(_ version: String) { defaults.set(version, forKey: lastSeenKey) }

    /// The stored install history, or empty when absent or not an array of strings.
    public func installHistory() -> [String] { defaults.object(forKey: historyKey) as? [String] ?? [] }

    /// Replaces the stored install history.
    public func setInstallHistory(_ history: [String]) { defaults.set(history, forKey: historyKey) }
}
