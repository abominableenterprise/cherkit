import Foundation

/// ``ReviewStore`` in `UserDefaults`, under `<keyPrefix>successCount` (integer), `<keyPrefix>isDue` (bool) and
/// `<keyPrefix>lastPromptedVersion` (string). A value of the wrong type counts as absent.
public final class UserDefaultsReviewStore: ReviewStore {
    /// The default key prefix.
    public static let defaultKeyPrefix = "cherkit.review."

    private let defaults: UserDefaults
    private let keyPrefix: String

    /// A store in `defaults` (e.g. `UserDefaults(suiteName: "group.com.example")`) with keys starting `keyPrefix`.
    public init(defaults: UserDefaults = .standard, keyPrefix: String = defaultKeyPrefix) {
        self.defaults = defaults
        self.keyPrefix = keyPrefix
    }

    private var countKey: String { keyPrefix + "successCount" }
    private var dueKey: String { keyPrefix + "isDue" }
    private var versionKey: String { keyPrefix + "lastPromptedVersion" }

    public func load() -> ReviewState {
        ReviewState(
            successCount: number(countKey).flatMap { Int(exactly: $0) } ?? 0,
            isDue: number(dueKey).flatMap(Self.bool) ?? false,
            lastPromptedVersion: defaults.object(forKey: versionKey) as? String
        )
    }

    public func save(_ state: ReviewState) {
        defaults.set(state.successCount, forKey: countKey)
        defaults.set(state.isDue, forKey: dueKey)
        if let version = state.lastPromptedVersion {
            defaults.set(version, forKey: versionKey)
        } else {
            defaults.removeObject(forKey: versionKey)
        }
    }

    /// Integers and booleans are both `NSNumber`s in a property list; strings and other types are not numbers.
    private func number(_ key: String) -> Double? {
        guard let value = defaults.object(forKey: key), !(value is String) else { return nil }
        return (value as? NSNumber)?.doubleValue
    }

    private static func bool(_ number: Double) -> Bool? {
        number == 1 ? true : number == 0 ? false : nil
    }
}
