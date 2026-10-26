import CherKitCore
import Foundation

/// One release's "What's new" notes, read from a bundled `whatsnew/<version>.json` file by ``NotesFile``.
public struct ReleaseNotes: Hashable, Sendable {
    /// The release this file describes.
    public let version: AppVersion
    /// Release date as written in the file (e.g. `2026-11-02`), or `nil` when absent. Shown as-is, never parsed.
    public let date: String?
    /// `false` for a fix-only release: no popup, and the store text is ``StoreText/fixOnlyText``.
    public let show: Bool
    /// Bullets per locale (`en`, `de`, `pt-BR`, ...), in file order. `en` is required when ``show`` is true.
    public let notes: [String: [String]]

    /// Creates notes directly; apps normally get them from ``NotesFile/read(_:fileName:)``.
    public init(version: AppVersion, date: String? = nil, show: Bool = true, notes: [String: [String]]) {
        self.version = version
        self.date = date
        self.show = show
        self.notes = notes
    }

    /// The bullets to display for the user's `preferredLocales` (e.g. `Locale.preferredLanguages`), resolved with
    /// ``LocaleFallback``; empty when no locale matches.
    public func bullets(preferredLocales: [String]) -> [String] {
        LocaleFallback.resolve(preferred: preferredLocales, available: notes.keys).flatMap { notes[$0] } ?? []
    }
}
