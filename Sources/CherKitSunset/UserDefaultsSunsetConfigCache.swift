import Foundation

/// Keeps the last good config body in `UserDefaults`, so Sunset still applies it when a launch is offline.
///
/// ``SunsetChecker`` saves only bodies that parsed (``SunsetConfigChoice/Source/fetched``); a malformed fetch never
/// replaces it. A value of another type under ``key`` counts as no cache.
public struct UserDefaultsSunsetConfigCache: SunsetConfigCache, @unchecked Sendable {
    // UserDefaults is documented as thread-safe; it is not marked Sendable.
    private let defaults: UserDefaults
    /// The key the body is stored under.
    public let key: String

    /// The default key.
    public static let defaultKey = "cherkit.sunset.config"

    /// A cache in `defaults` (e.g. `UserDefaults(suiteName: "group.com.example")`) under `key`.
    public init(defaults: UserDefaults = .standard, key: String = defaultKey) {
        self.defaults = defaults
        self.key = key
    }

    /// The saved body, or `nil` when there is none.
    public func load() async -> Data? {
        defaults.data(forKey: key)
    }

    /// Saves `data` under ``key``.
    public func save(_ data: Data) async {
        defaults.set(data, forKey: key)
    }
}
