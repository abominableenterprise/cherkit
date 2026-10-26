import Foundation

extension SunsetChecker {
    /// A checker that is switched off: ``check(installedVersion:osVersion:locale:)`` always answers
    /// ``SunsetDecision/upToDate`` without fetching anything or reading or writing any cache.
    ///
    /// Use it wherever the app should never be prompted, e.g. UI tests, simulators and emulators:
    ///
    /// ```swift
    /// let checker = isUITest ? .disabled : SunsetChecker(url: URL(string: "https://example.github.io/myapp/sunset.json")!)
    /// ```
    public static let disabled = SunsetChecker(
        source: DisabledSunsetConfigSource(),
        cache: DisabledSunsetConfigCache(),
        timeout: defaultTimeout,
        platform: .ios,
        isDisabled: true
    )
}

/// The source of ``SunsetChecker/disabled``: never fetches.
struct DisabledSunsetConfigSource: SunsetConfigSource {
    func fetch(timeout: TimeInterval) async -> Data? { nil }
}

/// The cache of ``SunsetChecker/disabled``: stores nothing.
struct DisabledSunsetConfigCache: SunsetConfigCache {
    func load() async -> Data? { nil }
    func save(_ data: Data) async {}
}
