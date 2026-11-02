package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.TelemetrySink

/** Called once per skipped notes file with its name and every problem found (for a log line; never shown to users). */
public typealias SkipHandler = (fileName: String, errors: List<NotesFileError>) -> Unit

/** Turns bundled notes files into [ReleaseNotes], skipping the files that are not valid. */
public object ReleaseNotesLoader {
    /**
     * The valid notes in [files] (file name -> content). Only names ending in `.json` are read, in file-name order,
     * each with [NotesFile.read]; a file that is not [NotesFileResult.isValid] is skipped and reported to [onSkip].
     * Never throws, so one bad file never hides the others.
     */
    public fun load(
        files: Map<String, ByteArray>,
        telemetrySink: TelemetrySink? = null,
        onSkip: SkipHandler? = null,
    ): List<ReleaseNotes> = loadReporting(files, onSkip, telemetrySink)

    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public fun load(files: Map<String, ByteArray>, onSkip: SkipHandler? = null): List<ReleaseNotes> =
        load(files, null, onSkip)

    /** Source compatibility for callers passing the callback positionally. */
    @Suppress("UNUSED_PARAMETER")
    public fun load(
        files: Map<String, ByteArray>,
        skipHandler: SkipHandler?,
        compatibility: Unit = Unit,
    ): List<ReleaseNotes> = load(files, null, skipHandler)

    internal fun loadReporting(
        files: Map<String, ByteArray>,
        onSkip: SkipHandler?,
        telemetrySink: TelemetrySink?,
        missingFiles: Set<String> = emptySet(),
    ): List<ReleaseNotes> {
        val names = files.keys.filter { it.endsWith(".json") }.sorted()
        if (names.isEmpty()) telemetrySink.notesError(1)
        return names.mapNotNull { name ->
            val result = NotesFile.read(files.getValue(name), name)
            val notes = result.notes
            if (result.isValid && notes != null) {
                notes
            } else {
                telemetrySink.notesError(if (name in missingFiles) 1 else 2)
                runCatching { onSkip?.invoke(name, result.errors) }
                null
            }
        }
    }
}
