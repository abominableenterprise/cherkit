import Foundation

/// The optional `testFlight` object in the `"ios"` block: the newest build the release pipeline uploaded to TestFlight.
///
/// ```json
/// "testFlight": {
///   "latestBuild": "123",
///   "url": "itms-beta://beta.itunes.apple.com/v1/app/123456789",
///   "notBefore": "2026-11-01T12:30:00Z",
///   "message": { "en": "A new test build is ready." }
/// }
/// ```
public struct SunsetTestFlightConfig: Equatable, Sendable {
    /// What a test-build prompt opens when the config has no ``url``: the TestFlight app.
    public static let defaultURL = URL(string: "itms-beta://")!

    /// TestFlight installs whose `CFBundleVersion` is below this get a soft ``SunsetUpdateKind/testBuild`` prompt.
    public let latestBuild: SunsetBuildNumber
    /// The link to open, `itms-beta://` or `https://` (any host); `nil` when absent or invalid.
    public let url: URL?
    /// No prompt while the device clock is before this; `nil` when absent or malformed (no delay).
    public let notBefore: Date?
    /// Custom prompt text by language tag, resolved like the block's `message`.
    public let messages: [String: String]

    /// Creates a block. ``SunsetConfig/parse(_:)`` validates hosted values; this initializer does not.
    public init(latestBuild: SunsetBuildNumber, url: URL? = nil, notBefore: Date? = nil, messages: [String: String] = [:]) {
        self.latestBuild = latestBuild
        self.url = url
        self.notBefore = notBefore
        self.messages = messages
    }
}
