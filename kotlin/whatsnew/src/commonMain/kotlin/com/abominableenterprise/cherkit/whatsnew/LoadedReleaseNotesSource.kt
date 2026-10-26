package com.abominableenterprise.cherkit.whatsnew

/**
 * [ReleaseNotesSource] over an app-supplied [loader] returning every bundled notes file as (file name, text) pairs,
 * e.g. the files in Android `assets/whatsnew/`. A [loader] that throws counts as no files. Invalid files are skipped
 * and reported to [onSkip]. Never throws.
 */
public class LoadedReleaseNotesSource(
    private val onSkip: SkipHandler? = null,
    private val loader: () -> List<Pair<String, String>>,
) : ReleaseNotesSource {
    override fun notesFiles(): Map<String, ByteArray> {
        val files = try {
            loader()
        } catch (_: Exception) {
            emptyList()
        }
        return files.associate { (name, text) -> name to text.encodeToByteArray() }
    }

    override fun releaseNotes(): List<ReleaseNotes> = ReleaseNotesLoader.load(notesFiles(), onSkip)
}
