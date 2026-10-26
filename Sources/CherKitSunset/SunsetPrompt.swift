import Foundation

/// What an update prompt shows and where it links.
public struct SunsetPrompt: Equatable, Sendable {
    /// The store page to open, from the config's `storeURL`.
    public let storeURL: URL
    /// Custom text in the user's language, or `nil` when the config has none that fits: the UI uses its default text.
    public let message: String?

    /// Creates a prompt.
    public init(storeURL: URL, message: String?) {
        self.storeURL = storeURL
        self.message = message
    }
}
