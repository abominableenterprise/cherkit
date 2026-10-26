import Foundation

/// Which config to apply: a fresh fetch, else the cached last good config, else none.
public struct SunsetConfigChoice: Equatable, Sendable {
    /// Where ``config`` came from.
    public enum Source: String, Equatable, Sendable {
        /// The fetched body parsed: apply it and save it with ``SunsetConfigCache/save(_:)``.
        case fetched
        /// The fetch failed or was unusable; the cached last good config parsed.
        case cached
        /// Neither parsed: ``config`` is `nil` and the decision is `upToDate` (fail open).
        case missing
    }

    /// Where ``config`` came from.
    public let source: Source
    /// The config to pass to ``SunsetDecider/decide(config:platform:appVersion:osVersion:languages:)``.
    public let config: SunsetConfig?

    /// Chooses between `fetched` (the body just downloaded, `nil` on failure or timeout) and `cached` (the last good
    /// body, `nil` when none). An unusable fetch never wins, so only ``Source/fetched`` bodies should be cached.
    public static func choose(fetched: Data?, cached: Data?) -> SunsetConfigChoice {
        if let data = fetched, let config = SunsetConfig.parse(data) { return SunsetConfigChoice(source: .fetched, config: config) }
        if let data = cached, let config = SunsetConfig.parse(data) { return SunsetConfigChoice(source: .cached, config: config) }
        return SunsetConfigChoice(source: .missing, config: nil)
    }
}
