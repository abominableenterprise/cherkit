import CherKitCore
import Foundation

/// The store-submission check: the version being submitted must have exactly one valid notes file.
/// Wiring it into each app's release step comes later. Contract: `fixtures/whatsnew/submission-gate.json`.
public enum SubmissionGate {
    /// Checks `version` against `files` (file name or path -> content, e.g. every `whatsnew/*.json`). A file belongs
    /// to the version when ``NotesFileRules/notesFileVersion(_:)`` equals it, so `1.4.json` counts for `1.4.0`.
    /// Files for other versions are not checked.
    public static func check(version: String, files: [String: Data]) -> SubmissionGateResult {
        guard let target = AppVersion(version) else {
            return .invalid([NotesFileRules.error(.invalidVersion, "Version \"\(version)\" is not a valid version.")])
        }
        let matching = files.keys.filter { NotesFileRules.notesFileVersion($0) == target }.sorted()
        guard let fileName = matching.first else { return .missing }
        guard matching.count == 1 else {
            return .invalid([NotesFileRules.error(.duplicateFile,
                "More than one notes file for version \"\(target)\": \(matching.joined(separator: ", ")).")])
        }
        let result = NotesFile.read(files[fileName] ?? Data(), fileName: fileName)
        return result.isValid ? .ok : .invalid(result.errors)
    }
}

/// Result of ``SubmissionGate/check(version:files:)``.
public enum SubmissionGateResult: Hashable, Sendable {
    /// The version has one valid notes file: submission may go ahead.
    case ok
    /// No notes file for the version: add `whatsnew/<version>.json`.
    case missing
    /// The notes file (or the version) has problems; fix every error.
    case invalid([NotesFileError])
}
