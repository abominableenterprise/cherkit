package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.TelemetrySink

/**
 * Source of the notes files bundled in the app. [LoadedReleaseNotesSource] wraps an app-supplied loader; on Android,
 * `AndroidWhatsNew.assetsSource` reads `assets/whatsnew/`.
 */
public interface ReleaseNotesSource {
    /** Every bundled notes file: file name (e.g. `1.4.0.json`) -> content. Never touches the network. */
    public fun notesFiles(): Map<String, ByteArray>

    /** The valid notes among [notesFiles]. The default reads them with [ReleaseNotesLoader.load]. */
    public fun releaseNotes(): List<ReleaseNotes> = ReleaseNotesLoader.load(notesFiles())

    /**
     * Sink-aware decoding for a checker. Sources overriding parsed [releaseNotes] can override this too;
     * the default reports individual invalid files, rather than treating a decode failure as missing notes.
     * A null sink preserves the original parsed-source override.
     */
    public fun releaseNotes(telemetrySink: TelemetrySink?): List<ReleaseNotes> =
        if (telemetrySink == null) releaseNotes() else ReleaseNotesLoader.load(notesFiles(), telemetrySink = telemetrySink)
}
