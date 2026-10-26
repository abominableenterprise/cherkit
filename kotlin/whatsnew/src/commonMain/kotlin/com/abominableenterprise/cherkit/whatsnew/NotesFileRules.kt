package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion

/** Content rules for a readable notes file (see [NotesFile]). */
public object NotesFileRules {
    /** Fewest bullets per locale. */
    public const val MIN_BULLETS: Int = 1

    /** Most bullets per locale: notes stay short. */
    public const val MAX_BULLETS: Int = 4

    /** Locale that every shown release must have. */
    public const val REQUIRED_LOCALE: String = "en"

    /**
     * The rule errors for [notes] read from [fileName], where [versionText] is the version as written in the file:
     * the file name must be `<version>.json`; when [ReleaseNotes.show] is true there must be [REQUIRED_LOCALE] notes
     * and each locale's store text must fit [StoreText.PLAY_LIMIT]; every locale needs 1 to 4 non-empty bullets
     * (empty means blank after trimming ASCII whitespace). Locales are checked in sorted order.
     */
    public fun check(notes: ReleaseNotes, versionText: String, fileName: String): List<NotesFileError> {
        val errors = mutableListOf<NotesFileError>()
        val name = fileName.substringAfterLast('/')
        if (notesFileVersion(fileName) != notes.version) {
            errors += error(
                NotesFileErrorCode.FILE_NAME_MISMATCH,
                "File name \"$name\" does not match version \"$versionText\"; expected \"$versionText.json\".",
            )
        }
        if (notes.show && notes.notes[REQUIRED_LOCALE] == null) {
            errors += error(NotesFileErrorCode.MISSING_ENGLISH, "\"show\" is true but there are no \"$REQUIRED_LOCALE\" notes.")
        }
        val locales = notes.notes.keys.sorted()
        for (locale in locales) {
            val bullets = notes.notes[locale].orEmpty()
            if (bullets.size !in MIN_BULLETS..MAX_BULLETS) {
                errors += error(
                    NotesFileErrorCode.BULLET_COUNT,
                    "\"notes.$locale\" has ${bullets.size} bullets; use $MIN_BULLETS to $MAX_BULLETS.",
                )
            }
            bullets.forEachIndexed { index, bullet ->
                if (bullet.trimAsciiWhitespace().isEmpty()) {
                    errors += error(NotesFileErrorCode.EMPTY_BULLET, "\"notes.$locale\" bullet ${index + 1} is empty.")
                }
            }
        }
        if (!notes.show) return errors
        for (locale in locales) {
            val length = StoreText.render(notes.notes[locale].orEmpty()).length
            if (length > StoreText.PLAY_LIMIT) {
                errors += error(
                    NotesFileErrorCode.STORE_TEXT_TOO_LONG,
                    "\"notes.$locale\" is $length characters as store text; Play allows ${StoreText.PLAY_LIMIT}.",
                )
            }
        }
        return errors
    }

    /**
     * The version a notes file name stands for: `whatsnew/1.4.json` -> `1.4`. `null` unless the name is
     * `<digits and dots>.json` and parses as an [AppVersion] (so `v1.4.json` and `1.4 (2).json` do not count).
     */
    public fun notesFileVersion(fileName: String): AppVersion? {
        val name = fileName.substringAfterLast('/')
        if (!name.endsWith(".json")) return null
        val stem = name.removeSuffix(".json")
        if (!stem.all { it == '.' || it in '0'..'9' }) return null
        return AppVersion.parse(stem)
    }

    internal fun error(code: NotesFileErrorCode, message: String): NotesFileError = NotesFileError(code, message)
}
