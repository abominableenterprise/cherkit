package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.TelemetrySink

/**
 * Bundled notes from an app-supplied loader. Missing/unreadable files fail open; invalid files are skipped.
 * A source's own sink takes precedence over a checker's sink, so each file failure is reported only once.
 */
public class LoadedReleaseNotesSource private constructor(
    private val onSkip: SkipHandler?,
    private val telemetrySink: TelemetrySink?,
    private val loadFiles: () -> NotesFiles,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : ReleaseNotesSource {
    public constructor(
        onSkip: SkipHandler? = null,
        telemetrySink: TelemetrySink? = null,
        loader: () -> List<Pair<String, String>>,
    ) : this(onSkip, telemetrySink, { NotesFiles(loader().associate { (name, text) -> name to text.encodeToByteArray() }) }, Unit)

    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(onSkip: SkipHandler? = null, loader: () -> List<Pair<String, String>>) :
        this(onSkip, null, loader)

    /** Source compatibility for callers passing both callbacks positionally. */
    @Suppress("UNUSED_PARAMETER")
    public constructor(
        legacyOnSkip: SkipHandler?,
        legacyLoader: () -> List<Pair<String, String>>,
        compatibility: Unit = Unit,
    ) : this(legacyOnSkip, null, legacyLoader)

    private fun readFiles(): NotesFiles = try {
        loadFiles()
    } catch (_: Exception) {
        NotesFiles(emptyMap())
    }

    override fun notesFiles(): Map<String, ByteArray> = readFiles().files

    override fun releaseNotes(): List<ReleaseNotes> = releaseNotes(null)

    override fun releaseNotes(telemetrySink: TelemetrySink?): List<ReleaseNotes> {
        val files = readFiles()
        return ReleaseNotesLoader.loadReporting(files.files, onSkip, this.telemetrySink ?: telemetrySink, files.missing)
    }

    internal companion object {
        fun fromFiles(onSkip: SkipHandler?, telemetrySink: TelemetrySink?, loader: () -> NotesFiles): LoadedReleaseNotesSource =
            LoadedReleaseNotesSource(onSkip, telemetrySink, loader, Unit)
    }
}

internal data class NotesFiles(val files: Map<String, ByteArray>, val missing: Set<String> = emptySet())
