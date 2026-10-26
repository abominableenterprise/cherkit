package com.abominableenterprise.cherkit.whatsnew

/** Called once per skipped notes file with its name and every problem found (for a log line; never shown to users). */
public typealias SkipHandler = (fileName: String, errors: List<NotesFileError>) -> Unit

/** Turns bundled notes files into [ReleaseNotes], skipping the files that are not valid. */
public object ReleaseNotesLoader {
    /**
     * The valid notes in [files] (file name -> content). Only names ending in `.json` are read, in file-name order,
     * each with [NotesFile.read]; a file that is not [NotesFileResult.isValid] is skipped and reported to [onSkip].
     * Never throws, so one bad file never hides the others.
     */
    public fun load(files: Map<String, ByteArray>, onSkip: SkipHandler? = null): List<ReleaseNotes> =
        files.keys.filter { it.endsWith(".json") }.sorted().mapNotNull { name ->
            val result = NotesFile.read(files.getValue(name), name)
            val notes = result.notes
            if (result.isValid && notes != null) {
                notes
            } else {
                onSkip?.invoke(name, result.errors)
                null
            }
        }
}
