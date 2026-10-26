package com.abominableenterprise.cherkit.whatsnew

/**
 * What [NotesFile.read] found in one notes file.
 *
 * @property notes The parsed notes, or `null` when the file is not readable at all (malformed JSON, missing or
 *   invalid version, wrong field types). Can be non-`null` while [errors] is not empty: check [isValid].
 * @property errors Every problem found, in a stable order; empty when the file is valid.
 */
public data class NotesFileResult(public val notes: ReleaseNotes?, public val errors: List<NotesFileError>) {
    /** `true` when the file is readable and has no errors. */
    public val isValid: Boolean get() = notes != null && errors.isEmpty()
}
