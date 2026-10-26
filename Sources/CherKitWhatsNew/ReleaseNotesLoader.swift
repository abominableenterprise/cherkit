import Foundation

/// Turns bundled notes files into ``ReleaseNotes``, skipping the files that are not valid.
public enum ReleaseNotesLoader {
    /// Called once per skipped file with its name and every problem found (for a log line; never shown to users).
    public typealias SkipHandler = @Sendable (_ fileName: String, _ errors: [NotesFileError]) -> Void

    /// The valid notes in `files` (file name -> content). Only names ending in `.json` are read, in file-name order,
    /// each with ``NotesFile/read(_:fileName:)``; a file that is not ``NotesFileResult/isValid`` is skipped and
    /// reported to `onSkip`. Never throws, so one bad file never hides the others.
    public static func load(_ files: [String: Data], onSkip: SkipHandler? = nil) -> [ReleaseNotes] {
        files.keys.filter { $0.hasSuffix(".json") }.sorted().compactMap { name in
            let result = NotesFile.read(files[name] ?? Data(), fileName: name)
            guard result.isValid, let notes = result.notes else {
                onSkip?(name, result.errors)
                return nil
            }
            return notes
        }
    }
}
