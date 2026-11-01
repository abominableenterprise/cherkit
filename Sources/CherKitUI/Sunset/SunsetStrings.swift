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
    /// Title of the soft TestFlight prompt (a ``SunsetUpdateKind/testBuild`` prompt).
    public var testBuildTitle: String
    /// TestFlight prompt text when the config's `testFlight` block has no custom message.
    public var testBuildMessage: String
    /// VoiceOver hint for ``updateButton`` on the TestFlight prompt.
    public var testBuildUpdateHint: String

    /// Creates the strings, with English defaults for anything you leave out.
    public init(
        softTitle: String = "Update available",
        softMessage: String = "A new version of the app is ready, with the latest improvements and fixes.",
        hardTitle: String = "Update required",
        hardMessage: String = "This version is no longer supported. Please update to keep using the app.",
        updateButton: String = "Update",
        updateHint: String = "Opens the store to update the app.",
        notNowButton: String = "Not now",
        testBuildTitle: String = "A new test build is available",
        testBuildMessage: String = "Install it from TestFlight to try the latest changes.",
        testBuildUpdateHint: String = "Opens TestFlight to install the new build."
    ) {
        self.softTitle = softTitle
        self.softMessage = softMessage
        self.hardTitle = hardTitle
        self.hardMessage = hardMessage
        self.updateButton = updateButton
        self.updateHint = updateHint
        self.notNowButton = notNowButton
        self.testBuildTitle = testBuildTitle
        self.testBuildMessage = testBuildMessage
        self.testBuildUpdateHint = testBuildUpdateHint
    }

    /// The soft prompt's title: ``testBuildTitle`` for a ``SunsetUpdateKind/testBuild`` prompt, else ``softTitle``.
    public func softTitle(for prompt: SunsetPrompt?) -> String {
        prompt?.kind == .testBuild ? testBuildTitle : softTitle
    }

    /// The text for the soft prompt: the config's custom message, or, when it has none, ``testBuildMessage`` for a
    /// ``SunsetUpdateKind/testBuild`` prompt and ``softMessage`` for a release prompt.
    public func softText(for prompt: SunsetPrompt) -> String {
        Self.custom(prompt.message) ?? (prompt.kind == .testBuild ? testBuildMessage : softMessage)
    }

    /// The VoiceOver hint for the soft prompt's ``updateButton``: ``testBuildUpdateHint`` for a
    /// ``SunsetUpdateKind/testBuild`` prompt, else ``updateHint``.
    public func softUpdateHint(for prompt: SunsetPrompt) -> String {
        prompt.kind == .testBuild ? testBuildUpdateHint : updateHint
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
