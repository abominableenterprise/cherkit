package com.abominableenterprise.cherkit.whatsnew

/**
 * Source of the notes files bundled in the app. [LoadedReleaseNotesSource] wraps an app-supplied loader; on Android,
 * `AndroidWhatsNew.assetsSource` reads `assets/whatsnew/`.
 */
public interface ReleaseNotesSource {
    /** Every bundled notes file: file name (e.g. `1.4.0.json`) -> content. Never touches the network. */
    public fun notesFiles(): Map<String, ByteArray>

    /** The valid notes among [notesFiles]. The default reads them with [ReleaseNotesLoader.load]. */
    public fun releaseNotes(): List<ReleaseNotes> = ReleaseNotesLoader.load(notesFiles())
}
