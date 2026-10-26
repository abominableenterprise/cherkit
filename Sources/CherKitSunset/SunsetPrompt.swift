import CherKitCore
import Foundation

/// What an update prompt shows and where it links.
public struct SunsetPrompt: Equatable, Sendable {
    /// The store page to open, from the config's `storeURL`.
    public let storeURL: URL
    /// Custom text in the user's language, or `nil` when the config has none that fits: the UI uses its default text.
    public let message: String?
    /// The config's `latestVersion` for this platform, or `nil` when the config has none (e.g. a hard block from
    /// `minimumVersion` alone). The UI remembers "Not now" for this version.
    public let latestVersion: AppVersion?

    /// Creates a prompt.
    public init(storeURL: URL, message: String?, latestVersion: AppVersion? = nil) {
        self.storeURL = storeURL
        self.message = message
        self.latestVersion = latestVersion
    }
}
