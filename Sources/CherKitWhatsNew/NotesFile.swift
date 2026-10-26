import CherKitCore
import Foundation

/// Reads and validates one bundled notes file, `whatsnew/<version>.json`:
///
/// ```json
/// { "version": "1.4.0", "date": "2026-11-02", "show": true,
///   "notes": { "en": ["You can now export to CSV."], "de": ["Export als CSV."] } }
/// ```
///
/// `version` is required; `date` is optional; `show` defaults to `true`; `notes` defaults to `{}`; unknown fields are
/// ignored. The rules are in ``NotesFileRules`` and the contract is `fixtures/whatsnew/notes-file.json`.
public enum NotesFile {
    /// Parses and validates `data`, the content of the file called `fileName` (a name or path such as
    /// `whatsnew/1.4.0.json`). Never throws: every problem is a ``NotesFileError`` in the result.
    public static func read(_ data: Data, fileName: String) -> NotesFileResult {
        guard let root = try? JSONDecoder().decode(JSONValue.self, from: data), case .object(let fields) = root else {
            return NotesFileResult(notes: nil, errors: [NotesFileRules.error(.notJSONObject, "The file is not a JSON object.")])
        }
        var errors: [NotesFileError] = []
        let versionText = field(fields["version"], name: "version", required: true, errors: &errors) { value in
            if case .string(let text) = value { return text } else { return nil }
        }
        let date = field(fields["date"], name: "date", errors: &errors) { value in
            if case .string(let text) = value { return text } else { return nil }
        }
        let show = field(fields["show"], name: "show", type: "true or false", errors: &errors) { value in
            if case .bool(let flag) = value { return flag } else { return nil }
        } ?? true
        let notes = readNotes(fields["notes"], errors: &errors)

        var version: AppVersion?
        if let versionText {
            version = AppVersion(versionText)
            if version == nil {
                errors.append(NotesFileRules.error(.invalidVersion, "\"version\" is not a valid version: \"\(versionText)\"."))
            }
        }
        guard let version, let notes else { return NotesFileResult(notes: nil, errors: errors) }
        let parsed = ReleaseNotes(version: version, date: date, show: show, notes: notes)
        errors += NotesFileRules.check(parsed, versionText: versionText ?? "", fileName: fileName)
        return NotesFileResult(notes: parsed, errors: errors)
    }

    /// Reads an optional field; `null` counts as absent. A wrong type adds a `wrongType` error.
    private static func field<T>(
        _ value: JSONValue?, name: String, type: String = "a string", required: Bool = false,
        errors: inout [NotesFileError], extract: (JSONValue) -> T?
    ) -> T? {
        guard let value, !isNull(value) else {
            if required { errors.append(NotesFileRules.error(.missingField, "Missing required field \"\(name)\".")) }
            return nil
        }
        guard let extracted = extract(value) else {
            errors.append(NotesFileRules.error(.wrongType, "\"\(name)\" must be \(type)."))
            return nil
        }
        return extracted
    }

    /// Reads `notes`; returns `nil` (with errors) when it or any locale has the wrong type.
    private static func readNotes(_ value: JSONValue?, errors: inout [NotesFileError]) -> [String: [String]]? {
        guard let value, !isNull(value) else { return [:] }
        guard case .object(let locales) = value else {
            errors.append(NotesFileRules.error(
                .wrongType, "\"notes\" must be an object mapping locales to lists of strings."))
            return nil
        }
        var notes: [String: [String]] = [:]
        var valid = true
        for locale in locales.keys.sorted() {
            let bullets: [String]? = {
                guard case .array(let items) = locales[locale] else { return nil }
                let strings = items.compactMap { item -> String? in
                    if case .string(let text) = item { return text } else { return nil }
                }
                return strings.count == items.count ? strings : nil
            }()
            if let bullets {
                notes[locale] = bullets
            } else {
                errors.append(NotesFileRules.error(.wrongType, "\"notes.\(locale)\" must be a list of strings."))
                valid = false
            }
        }
        return valid ? notes : nil
    }

    private static func isNull(_ value: JSONValue) -> Bool {
        if case .null = value { return true } else { return false }
    }
}
