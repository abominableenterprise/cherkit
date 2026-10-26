import CherKitCore

/// Content rules for a readable notes file (see ``NotesFile``).
public enum NotesFileRules {
    /// Fewest bullets per locale.
    public static let minBullets = 1
    /// Most bullets per locale: notes stay short.
    public static let maxBullets = 4
    /// Locale that every shown release must have.
    public static let requiredLocale = "en"

    /// The rule errors for `notes` read from `fileName`, where `versionText` is the version as written in the file:
    /// the file name must be `<version>.json`; when ``ReleaseNotes/show`` is true there must be ``requiredLocale``
    /// notes and each locale's store text must fit ``StoreText/playLimit``; every locale needs 1 to 4 non-empty
    /// bullets (empty means blank after trimming ASCII whitespace). Locales are checked in sorted order.
    public static func check(_ notes: ReleaseNotes, versionText: String, fileName: String) -> [NotesFileError] {
        var errors: [NotesFileError] = []
        let name = lastPathComponent(fileName)
        if notesFileVersion(fileName) != notes.version {
            errors.append(error(.fileNameMismatch,
                "File name \"\(name)\" does not match version \"\(versionText)\"; expected \"\(versionText).json\"."))
        }
        if notes.show, notes.notes[requiredLocale] == nil {
            errors.append(error(.missingEnglish, "\"show\" is true but there are no \"\(requiredLocale)\" notes."))
        }
        let locales = notes.notes.keys.sorted()
        for locale in locales {
            let bullets = notes.notes[locale] ?? []
            if !(minBullets...maxBullets).contains(bullets.count) {
                errors.append(error(.bulletCount,
                    "\"notes.\(locale)\" has \(bullets.count) bullets; use \(minBullets) to \(maxBullets)."))
            }
            for (index, bullet) in bullets.enumerated() where bullet.trimmingASCIIWhitespace.isEmpty {
                errors.append(error(.emptyBullet, "\"notes.\(locale)\" bullet \(index + 1) is empty."))
            }
        }
        guard notes.show else { return errors }
        for locale in locales {
            let length = StoreText.render(notes.notes[locale] ?? []).utf16.count
            if length > StoreText.playLimit {
                errors.append(error(.storeTextTooLong,
                    "\"notes.\(locale)\" is \(length) characters as store text; Play allows \(StoreText.playLimit)."))
            }
        }
        return errors
    }

    /// The version a notes file name stands for: `whatsnew/1.4.json` -> `1.4`. `nil` unless the name is
    /// `<digits and dots>.json` and parses as an `AppVersion` (so `v1.4.json` and `1.4 (2).json` do not count).
    public static func notesFileVersion(_ fileName: String) -> AppVersion? {
        let name = lastPathComponent(fileName)
        guard name.hasSuffix(".json") else { return nil }
        let stem = name.dropLast(".json".count)
        guard stem.unicodeScalars.allSatisfy({ $0 == "." || ("0"..."9").contains($0) }) else { return nil }
        return AppVersion(String(stem))
    }

    static func error(_ code: NotesFileErrorCode, _ message: String) -> NotesFileError {
        NotesFileError(code: code, message: message)
    }

    private static func lastPathComponent(_ path: String) -> String {
        String(path.split(separator: "/", omittingEmptySubsequences: false).last ?? "")
    }
}
