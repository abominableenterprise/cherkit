#if canImport(SwiftUI)
import CherKitSunset
import Foundation

/// The text of the Sunset prompts. English defaults; pass your own (already localized) text to override any of them.
public struct SunsetStrings: Equatable, Sendable {
    /// Title of the soft, dismissible prompt.
    public var softTitle: String
    /// Soft prompt text when the config has no custom message.
    public var softMessage: String
    /// Title of the hard block.
    public var hardTitle: String
    /// Hard block text when the config has no custom message.
    public var hardMessage: String
    /// The button that opens the store page.
    public var updateButton: String
    /// VoiceOver hint for ``updateButton``.
    public var updateHint: String
    /// The soft prompt's dismiss button. Tapping it hides the prompt for that version.
    public var notNowButton: String

    /// Creates the strings, with English defaults for anything you leave out.
    public init(
        softTitle: String = "Update available",
        softMessage: String = "A new version of the app is ready, with the latest improvements and fixes.",
        hardTitle: String = "Update required",
        hardMessage: String = "This version is no longer supported. Please update to keep using the app.",
        updateButton: String = "Update",
        updateHint: String = "Opens the store to update the app.",
        notNowButton: String = "Not now"
    ) {
        self.softTitle = softTitle
        self.softMessage = softMessage
        self.hardTitle = hardTitle
        self.hardMessage = hardMessage
        self.updateButton = updateButton
        self.updateHint = updateHint
        self.notNowButton = notNowButton
    }

    /// The text for the soft prompt: the config's custom message, or ``softMessage`` when it has none.
    public func softText(for prompt: SunsetPrompt) -> String {
        Self.custom(prompt.message) ?? softMessage
    }

    /// The text for the hard block: the config's custom message, or ``hardMessage`` when it has none.
    public func hardText(for prompt: SunsetPrompt) -> String {
        Self.custom(prompt.message) ?? hardMessage
    }

    /// `message`, or `nil` when it is missing or only whitespace (so the screen never shows an empty line).
    private static func custom(_ message: String?) -> String? {
        guard let message, !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return message
    }
}
#endif
