/// The App Store / Play "What's New" text for a release, built from the same notes file the app shows.
/// The contract is `fixtures/whatsnew/store-text.json`.
public enum StoreText {
    /// Play's limit for "What's new" text, in characters.
    public static let playLimit = 500
    /// Store text for a fix-only release (`"show": false`).
    public static let fixOnlyText = "Bug fixes and improvements."

    /// The store text for `notes` in the store locale `locale` (e.g. `de-DE`), resolved with ``LocaleFallback``.
    /// Bullets are trimmed, empty ones skipped, and each written as `• <bullet>` on its own line. Over `limit`
    /// characters (UTF-16 code units, never fewer than Play counts) the result is ``StoreTextResult/tooLong(length:)``:
    /// the text is never truncated, so the notes must be shortened.
    public static func make(for notes: ReleaseNotes, locale: String, limit: Int = playLimit) -> StoreTextResult {
        let text: String
        if notes.show {
            text = render(notes.bullets(preferredLocales: [locale]))
            if text.isEmpty { return .noNotes }
        } else {
            text = fixOnlyText
        }
        let length = text.utf16.count
        return length > limit ? .tooLong(length: length) : .text(text)
    }

    /// `bullets` as store text: trimmed, empty ones skipped, `• <bullet>` joined with newlines.
    static func render(_ bullets: [String]) -> String {
        bullets.map(\.trimmingASCIIWhitespace).filter { !$0.isEmpty }.map { "• " + $0 }.joined(separator: "\n")
    }
}

/// Result of ``StoreText/make(for:locale:limit:)``.
public enum StoreTextResult: Hashable, Sendable {
    /// Text ready to paste into the store field.
    case text(String)
    /// The text would be `length` characters, over the limit; shorten the notes.
    case tooLong(length: Int)
    /// Neither the locale nor `en` has any non-empty bullet.
    case noNotes
}
