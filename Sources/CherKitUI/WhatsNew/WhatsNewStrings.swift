#if canImport(SwiftUI)
import CherKitWhatsNew

/// The user-facing text of ``WhatsNewSheet`` and ``WhatsNewHistoryView``. English by default; pass your own
/// (already localized) strings to override any of them, e.g. `WhatsNewStrings(title: "Neuigkeiten")`.
public struct WhatsNewStrings: Sendable {
    /// Sheet title. Default: "What's New".
    public var title: String
    /// The sheet's dismiss button. Default: "Continue".
    public var continueButton: String
    /// Heading for one release, from its version (e.g. `1.4.0`). Default: "Version 1.4.0".
    public var versionTitle: @Sendable (String) -> String
    /// Navigation title of the history list. Default: "Version History".
    public var historyTitle: String
    /// Badge on the installed release in the history list. Default: "You're on this version".
    public var installedBadge: String
    /// Shown for a history release without bullets (fix-only). Default: "Bug fixes and improvements.".
    public var noNotes: String
    /// Shown when the history list has no releases. Default: "No release notes yet.".
    public var emptyHistory: String

    /// English strings; override any parameter.
    public init(
        title: String = "What's New",
        continueButton: String = "Continue",
        versionTitle: @escaping @Sendable (String) -> String = { "Version \($0)" },
        historyTitle: String = "Version History",
        installedBadge: String = "You're on this version",
        noNotes: String = StoreText.fixOnlyText,
        emptyHistory: String = "No release notes yet."
    ) {
        self.title = title
        self.continueButton = continueButton
        self.versionTitle = versionTitle
        self.historyTitle = historyTitle
        self.installedBadge = installedBadge
        self.noNotes = noNotes
        self.emptyHistory = emptyHistory
    }

    /// The English defaults.
    public static let english = WhatsNewStrings()
}
#endif
