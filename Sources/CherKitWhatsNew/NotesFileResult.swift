/// What ``NotesFile/read(_:fileName:)`` found in one notes file.
public struct NotesFileResult: Hashable, Sendable {
    /// The parsed notes, or `nil` when the file is not readable at all (malformed JSON, missing or invalid version,
    /// wrong field types). Can be non-`nil` while ``errors`` is not empty: callers should still check ``isValid``.
    public let notes: ReleaseNotes?
    /// Every problem found, in a stable order; empty when the file is valid.
    public let errors: [NotesFileError]

    /// Creates a result.
    public init(notes: ReleaseNotes?, errors: [NotesFileError]) {
        self.notes = notes
        self.errors = errors
    }

    /// `true` when the file is readable and has no errors.
    public var isValid: Bool { notes != nil && errors.isEmpty }
}
