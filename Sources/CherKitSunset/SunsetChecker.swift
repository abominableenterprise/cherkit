import Foundation

/// The one call apps make at launch: fetch the hosted config, fall back to the last good one, and decide.
///
/// ```swift
/// let checker = SunsetChecker(url: URL(string: "https://example.github.io/myapp/sunset.json")!)
/// switch await checker.check() {
/// case .upToDate: break
/// case .softUpdate(let prompt): showUpdateAvailable(prompt)
/// case .hardUpdate(let prompt): showUpdateRequired(prompt)
/// }
/// ```
///
/// It never throws and fails open: an unreachable, slow, non-2xx or malformed config uses the cached last good config,
/// and with no usable config at all the answer is ``SunsetDecision/upToDate``. Only a fetched body that parses is
/// saved to the cache.
///
/// To turn the check off (UI tests, simulators), use ``disabled``:
/// `let checker = isUITest ? .disabled : SunsetChecker(url: url)`.
public struct SunsetChecker: Sendable {
    /// The default fetch timeout in seconds. A launch never waits longer than this for the network.
    public static let defaultTimeout: TimeInterval = 5

    /// Where the hosted config comes from.
    public let source: SunsetConfigSource
    /// Where the last good config is kept.
    public let cache: SunsetConfigCache
    /// The fetch timeout in seconds.
    public let timeout: TimeInterval
    /// Which block of the config applies to this app.
    public let platform: SunsetPlatform
    /// `true` only for ``disabled``: ``check(installedVersion:osVersion:locale:)`` answers `upToDate` at once.
    let isDisabled: Bool

    /// A checker over any `source` and `cache`.
    public init(
        source: SunsetConfigSource,
        cache: SunsetConfigCache = UserDefaultsSunsetConfigCache(),
        timeout: TimeInterval = defaultTimeout,
        platform: SunsetPlatform = .ios
    ) {
        self.init(source: source, cache: cache, timeout: timeout, platform: platform, isDisabled: false)
    }

    init(source: SunsetConfigSource, cache: SunsetConfigCache, timeout: TimeInterval, platform: SunsetPlatform, isDisabled: Bool) {
        self.source = source
        self.cache = cache
        self.timeout = timeout
        self.platform = platform
        self.isDisabled = isDisabled
    }

    /// A checker that fetches `url` with `URLSession` and caches in `UserDefaults.standard`.
    public init(url: URL, cache: SunsetConfigCache = UserDefaultsSunsetConfigCache(), timeout: TimeInterval = defaultTimeout) {
        self.init(source: URLSessionSunsetConfigSource(url: url), cache: cache, timeout: timeout)
    }

    /// Decides for this launch. Never throws; every failure ends in the cached config or ``SunsetDecision/upToDate``.
    ///
    /// - Parameters:
    ///   - installedVersion: The installed app version, e.g. `CFBundleShortVersionString`.
    ///   - osVersion: The device OS version, e.g. `"17.4.1"` (on Android, the API level).
    ///   - locale: The user's preferred language tags in order, e.g. `Locale.preferredLanguages`.
    public func check(installedVersion: String, osVersion: String, locale: [String]) async -> SunsetDecision {
        if isDisabled { return .upToDate }
        let source = self.source
        let timeout = self.timeout
        let fetched = await SunsetTimeout.run(seconds: timeout) { await source.fetch(timeout: timeout) }
        let choice = SunsetConfigChoice.choose(fetched: fetched, cached: await cache.load())
        if choice.source == .fetched, let fetched { await cache.save(fetched) }
        return SunsetDecider.decide(
            config: choice.config,
            platform: platform,
            appVersion: installedVersion,
            osVersion: osVersion,
            languages: locale
        )
    }

    /// ``check(installedVersion:osVersion:locale:)`` for the running app: its `CFBundleShortVersionString`, the
    /// `ProcessInfo` OS version and `Locale.preferredLanguages`.
    public func check(bundle: Bundle = .main, locale: [String] = Locale.preferredLanguages) async -> SunsetDecision {
        await check(installedVersion: Self.installedVersion(bundle: bundle), osVersion: Self.osVersion(), locale: locale)
    }

    /// `bundle`'s `CFBundleShortVersionString`, or `""` (which fails open) when absent.
    public static func installedVersion(bundle: Bundle = .main) -> String {
        bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    /// The OS version as `major.minor.patch`, e.g. `"17.4.1"`.
    public static func osVersion(_ processInfo: ProcessInfo = .processInfo) -> String {
        let version = processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
    }
}
