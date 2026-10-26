/// One readable problem found in a notes file. ``code`` is stable; ``message`` is for people (CI logs, release step).
public struct NotesFileError: Hashable, Sendable, CustomStringConvertible {
    /// Machine-readable kind of problem, shared with Kotlin and `fixtures/whatsnew/`.
    public let code: NotesFileErrorCode
    /// English sentence describing the problem, e.g. `"notes.en" bullet 2 is empty.`
    public let message: String

    /// Creates an error.
    public init(code: NotesFileErrorCode, message: String) {
        self.code = code
        self.message = message
    }

    /// The ``message``.
    public var description: String { message }
}

/// Kinds of ``NotesFileError``. Raw values match the `code` strings in `fixtures/whatsnew/`.
public enum NotesFileErrorCode: String, Hashable, Sendable, CaseIterable {
    /// The file is not valid JSON, or its top level is not an object.
    case notJSONObject
    /// A required field (`version`) is missing.
    case missingField
    /// A field has the wrong JSON type.
    case wrongType
    /// A version string does not parse as an `AppVersion`.
    case invalidVersion
    /// The file name is not `<version>.json` for the version inside it.
    case fileNameMismatch
    /// `show` is true but there are no `en` notes.
    case missingEnglish
    /// A locale has fewer than 1 or more than 4 bullets.
    case bulletCount
    /// A bullet is empty or whitespace only.
    case emptyBullet
    /// A locale's store text is longer than Play's 500 characters.
    case storeTextTooLong
    /// More than one notes file exists for the same version (submission gate only).
    case duplicateFile
}
