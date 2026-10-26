package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Reads and validates one bundled notes file, `whatsnew/<version>.json`:
 *
 * ```json
 * { "version": "1.4.0", "date": "2026-11-02", "show": true,
 *   "notes": { "en": ["You can now export to CSV."], "de": ["Export als CSV."] } }
 * ```
 *
 * `version` is required; `date` is optional; `show` defaults to `true`; `notes` defaults to `{}`; unknown fields are
 * ignored. The rules are in [NotesFileRules] and the contract is `fixtures/whatsnew/notes-file.json`.
 */
public object NotesFile {
    /**
     * Parses and validates [data], the content of the file called [fileName] (a name or path such as
     * `whatsnew/1.4.0.json`). Never throws: every problem is a [NotesFileError] in the result.
     */
    public fun read(data: ByteArray, fileName: String): NotesFileResult {
        val root = try {
            Json.parseToJsonElement(data.decodeToString(throwOnInvalidSequence = true))
        } catch (_: Exception) {
            null
        }
        val fields = root as? JsonObject
            ?: return NotesFileResult(null, listOf(NotesFileRules.error(NotesFileErrorCode.NOT_JSON_OBJECT, "The file is not a JSON object.")))
        val errors = mutableListOf<NotesFileError>()
        val versionText = field(fields["version"], "version", required = true, errors = errors) { it.stringOrNull() }
        val date = field(fields["date"], "date", errors = errors) { it.stringOrNull() }
        val show = field(fields["show"], "show", type = "true or false", errors = errors) { value ->
            (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
        } ?: true
        val notes = readNotes(fields["notes"], errors)

        var version: AppVersion? = null
        if (versionText != null) {
            version = AppVersion.parse(versionText)
            if (version == null) {
                errors += NotesFileRules.error(NotesFileErrorCode.INVALID_VERSION, "\"version\" is not a valid version: \"$versionText\".")
            }
        }
        if (version == null || notes == null) return NotesFileResult(null, errors)
        val parsed = ReleaseNotes(version, date, show, notes)
        errors += NotesFileRules.check(parsed, versionText ?: "", fileName)
        return NotesFileResult(parsed, errors)
    }

    /** Reads an optional field; `null` counts as absent. A wrong type adds a `wrongType` error. */
    private fun <T> field(
        value: JsonElement?,
        name: String,
        type: String = "a string",
        required: Boolean = false,
        errors: MutableList<NotesFileError>,
        extract: (JsonElement) -> T?,
    ): T? {
        if (value == null || value is JsonNull) {
            if (required) errors += NotesFileRules.error(NotesFileErrorCode.MISSING_FIELD, "Missing required field \"$name\".")
            return null
        }
        return extract(value) ?: run {
            errors += NotesFileRules.error(NotesFileErrorCode.WRONG_TYPE, "\"$name\" must be $type.")
            null
        }
    }

    /** Reads `notes`; returns `null` (with errors) when it or any locale has the wrong type. */
    private fun readNotes(value: JsonElement?, errors: MutableList<NotesFileError>): Map<String, List<String>>? {
        if (value == null || value is JsonNull) return emptyMap()
        val locales = value as? JsonObject ?: run {
            errors += NotesFileRules.error(
                NotesFileErrorCode.WRONG_TYPE, "\"notes\" must be an object mapping locales to lists of strings.",
            )
            return null
        }
        val notes = mutableMapOf<String, List<String>>()
        var valid = true
        for (locale in locales.keys.sorted()) {
            val items = locales[locale] as? JsonArray
            val bullets = items?.mapNotNull { it.stringOrNull() }?.takeIf { it.size == items.size }
            if (bullets != null) {
                notes[locale] = bullets
            } else {
                errors += NotesFileRules.error(NotesFileErrorCode.WRONG_TYPE, "\"notes.$locale\" must be a list of strings.")
                valid = false
            }
        }
        return if (valid) notes else null
    }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
