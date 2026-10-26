package com.abominableenterprise.cherkit.whatsnew

/**
 * One readable problem found in a notes file. [code] is stable; [message] is for people (CI logs, release step).
 *
 * @property code Machine-readable kind of problem, shared with Swift and `fixtures/whatsnew/`.
 * @property message English sentence describing the problem, e.g. `"notes.en" bullet 2 is empty.`
 */
public data class NotesFileError(public val code: NotesFileErrorCode, public val message: String) {
    /** The [message]. */
    override fun toString(): String = message
}

/** Kinds of [NotesFileError]. [rawValue] matches Swift's raw values and the `code` strings in `fixtures/whatsnew/`. */
public enum class NotesFileErrorCode(public val rawValue: String) {
    /** The file is not valid JSON (UTF-8), or its top level is not an object. */
    NOT_JSON_OBJECT("notJSONObject"),

    /** A required field (`version`) is missing. */
    MISSING_FIELD("missingField"),

    /** A field has the wrong JSON type. */
    WRONG_TYPE("wrongType"),

    /** A version string does not parse as an [com.abominableenterprise.cherkit.core.AppVersion]. */
    INVALID_VERSION("invalidVersion"),

    /** The file name is not `<version>.json` for the version inside it. */
    FILE_NAME_MISMATCH("fileNameMismatch"),

    /** `show` is true but there are no `en` notes. */
    MISSING_ENGLISH("missingEnglish"),

    /** A locale has fewer than 1 or more than 4 bullets. */
    BULLET_COUNT("bulletCount"),

    /** A bullet is empty or whitespace only. */
    EMPTY_BULLET("emptyBullet"),

    /** A locale's store text is longer than Play's 500 characters. */
    STORE_TEXT_TOO_LONG("storeTextTooLong"),

    /** More than one notes file exists for the same version (submission gate only). */
    DUPLICATE_FILE("duplicateFile"),
}
