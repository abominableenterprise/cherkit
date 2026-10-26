import Foundation

/// A parsed hosted Sunset config: one optional block per platform.
///
/// The hosted file is small public JSON, one per app:
///
/// ```json
/// {
///   "schemaVersion": 1,
///   "ios": {
///     "minimumVersion": "1.2.0", "latestVersion": "1.4.0", "latestMinimumOS": "17.0",
///     "storeURL": "https://apps.apple.com/app/id123", "message": { "en": "Please update.", "de": "Bitte aktualisieren." }
///   },
///   "android": { "minimumVersion": "1.2.0", "latestVersion": "1.4.0", "latestMinimumOS": "26", "storeURL": "https://play.google.com/store/apps/details?id=com.example" }
/// }
/// ```
///
/// Parsing fails open: see ``parse(_:)``. The contract is `fixtures/sunset/`, shared with Kotlin's `SunsetConfig`.
public struct SunsetConfig: Equatable, Sendable {
    /// The only `schemaVersion` this library understands. Any other value makes ``parse(_:)`` return `nil`.
    public static let supportedSchemaVersion = 1

    /// The `"ios"` block, or `nil` when it is missing or invalid.
    public let ios: SunsetPlatformConfig?
    /// The `"android"` block, or `nil` when it is missing or invalid.
    public let android: SunsetPlatformConfig?

    /// Creates a config from already validated blocks.
    public init(ios: SunsetPlatformConfig? = nil, android: SunsetPlatformConfig? = nil) {
        self.ios = ios
        self.android = android
    }

    /// The block for `platform`, or `nil` when the config has none.
    public func block(for platform: SunsetPlatform) -> SunsetPlatformConfig? {
        switch platform {
        case .ios: return ios
        case .android: return android
        }
    }

    /// Parses the hosted JSON, or returns `nil` when it is not a usable config (callers then fail open).
    ///
    /// `nil` for anything that is not a JSON object with `schemaVersion` equal to ``supportedSchemaVersion``.
    /// Unknown fields and platforms are ignored. A platform block that is not an object, has a version that is not a
    /// valid `AppVersion` string, or lacks a valid `https` `storeURL` is dropped (that platform never prompts); the
    /// other block is kept. A malformed `message` only drops the custom text.
    public static func parse(_ data: Data) -> SunsetConfig? {
        SunsetConfigParser.parse(data)
    }
}
