import CherKitCore

/// One release to show in the "What's new" sheet, from ``WhatsNewChecker/onLaunch(installedVersion:locale:)``.
public struct WhatsNewEntry: Hashable, Sendable {
    /// The release.
    public let version: AppVersion
    /// Release date as written in the notes file, or `nil`. Shown as-is.
    public let date: String?
    /// The bullets in the user's locale (see ``LocaleFallback``).
    public let bullets: [String]

    /// Creates an entry.
    public init(version: AppVersion, date: String?, bullets: [String]) {
        self.version = version
        self.date = date
        self.bullets = bullets
    }
}

/// One bundled release in the full history list, from ``WhatsNewChecker/history(locale:)``.
public struct WhatsNewHistoryEntry: Hashable, Sendable {
    /// The release.
    public let version: AppVersion
    /// Release date as written in the notes file, or `nil`. Shown as-is.
    public let date: String?
    /// The bullets in the user's locale; empty for a fix-only release without notes.
    public let bullets: [String]
    /// `true` for the version installed now ("you are here").
    public let isInstalled: Bool

    /// Creates an entry.
    public init(version: AppVersion, date: String?, bullets: [String], isInstalled: Bool) {
        self.version = version
        self.date = date
        self.bullets = bullets
        self.isInstalled = isInstalled
    }
}
