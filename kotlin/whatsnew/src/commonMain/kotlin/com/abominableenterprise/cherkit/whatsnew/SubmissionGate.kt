package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion

/**
 * The store-submission check: the version being submitted must have exactly one valid notes file.
 * Wiring it into each app's release step comes later. Contract: `fixtures/whatsnew/submission-gate.json`.
 */
public object SubmissionGate {
    /**
     * Checks [version] against [files] (file name or path -> content, e.g. every file in the app's `whatsnew` folder). A file belongs
     * to the version when [NotesFileRules.notesFileVersion] equals it, so `1.4.json` counts for `1.4.0`. Files for
     * other versions are not checked.
     */
    public fun check(version: String, files: Map<String, ByteArray>): SubmissionGateResult {
        val target = AppVersion.parse(version) ?: return SubmissionGateResult.Invalid(
            listOf(NotesFileRules.error(NotesFileErrorCode.INVALID_VERSION, "Version \"$version\" is not a valid version.")),
        )
        val matching = files.keys.filter { NotesFileRules.notesFileVersion(it) == target }.sorted()
        val fileName = matching.firstOrNull() ?: return SubmissionGateResult.Missing
        if (matching.size > 1) {
            return SubmissionGateResult.Invalid(
                listOf(
                    NotesFileRules.error(
                        NotesFileErrorCode.DUPLICATE_FILE,
                        "More than one notes file for version \"$target\": ${matching.joinToString(", ")}.",
                    ),
                ),
            )
        }
        val result = NotesFile.read(files.getValue(fileName), fileName)
        return if (result.isValid) SubmissionGateResult.Ok else SubmissionGateResult.Invalid(result.errors)
    }
}

/** Result of [SubmissionGate.check]. */
public sealed interface SubmissionGateResult {
    /** The version has one valid notes file: submission may go ahead. */
    public data object Ok : SubmissionGateResult

    /** No notes file for the version: add `whatsnew/<version>.json`. */
    public data object Missing : SubmissionGateResult

    /** The notes file (or the version) has problems; fix every [errors] entry. */
    public data class Invalid(public val errors: List<NotesFileError>) : SubmissionGateResult
}
