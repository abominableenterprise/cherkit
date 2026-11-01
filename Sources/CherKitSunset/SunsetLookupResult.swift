import CherKitCore
import Foundation

/// The parts of Apple's public App Store lookup (`https://itunes.apple.com/lookup?bundleId=<id>&country=<cc>`) that
/// Sunset uses: the store's current version, the OS it needs, and its store page.
public struct SunsetLookupResult: Equatable, Sendable {
    /// `results[0].version`: the version live in the App Store.
    public let version: AppVersion
    /// `results[0].minimumOsVersion`: devices below it cannot install ``version`` and get no lookup prompt.
    public let minimumOSVersion: AppVersion
    /// `results[0].trackViewUrl` when it is a plain `https` link (``SunsetConfig`` `storeURL` rules), else `nil`.
    public let trackViewURL: URL?

    /// Creates a result from already validated values.
    public init(version: AppVersion, minimumOSVersion: AppVersion, trackViewURL: URL? = nil) {
        self.version = version
        self.minimumOSVersion = minimumOSVersion
        self.trackViewURL = trackViewURL
    }

    /// Parses a lookup response, or returns `nil` (no lookup prompt: fail open) when it is not JSON, `resultCount`
    /// is not a number above 0, `results[0]` is not an object, or its `version` or `minimumOsVersion` is missing or not
    /// a valid `AppVersion` string. A missing or non-`https` `trackViewUrl` only drops ``trackViewURL``.
    /// The contract is `fixtures/sunset/lookup.json`, shared with Kotlin's `SunsetLookupResult.parse`.
    public static func parse(_ data: Data) -> SunsetLookupResult? {
        guard case .object(let root)? = try? JSONDecoder().decode(JSONValue.self, from: data),
              case .number(let count)? = root["resultCount"], count >= 1,
              case .array(let results)? = root["results"],
              case .object(let first)? = results.first,
              case .string(let versionText)? = first["version"], let version = AppVersion(versionText),
              case .string(let osText)? = first["minimumOsVersion"], let minimumOS = AppVersion(osText)
        else { return nil }
        var url: URL?
        if case .string(let urlText)? = first["trackViewUrl"] { url = SunsetStoreURL.validate(urlText) }
        return SunsetLookupResult(version: version, minimumOSVersion: minimumOS, trackViewURL: url)
    }
}
