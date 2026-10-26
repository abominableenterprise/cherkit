package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion

/**
 * One release's "What's new" notes, read from a bundled `whatsnew/<version>.json` file by [NotesFile].
 *
 * @property version The release this file describes.
 * @property date Release date as written in the file (e.g. `2026-11-02`), or `null` when absent. Never parsed.
 * @property show `false` for a fix-only release: no popup, and the store text is [StoreText.FIX_ONLY_TEXT].
 * @property notes Bullets per locale (`en`, `de`, `pt-BR`, ...), in file order. `en` is required when [show] is true.
 */
public data class ReleaseNotes(
    public val version: AppVersion,
    public val date: String? = null,
    public val show: Boolean = true,
    public val notes: Map<String, List<String>>,
) {
    /**
     * The bullets to display for the user's [preferredLocales] (most preferred first), resolved with
     * [LocaleFallback]; empty when no locale matches.
     */
    public fun bullets(preferredLocales: List<String>): List<String> =
        LocaleFallback.resolve(preferredLocales, notes.keys)?.let { notes[it] } ?: emptyList()
}
