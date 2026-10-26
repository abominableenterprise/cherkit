package com.abominableenterprise.cherkit.whatsnew

/**
 * The App Store / Play "What's New" text for a release, built from the same notes file the app shows.
 * The contract is `fixtures/whatsnew/store-text.json`.
 */
public object StoreText {
    /** Play's limit for "What's new" text, in characters. */
    public const val PLAY_LIMIT: Int = 500

    /** Store text for a fix-only release (`"show": false`). */
    public const val FIX_ONLY_TEXT: String = "Bug fixes and improvements."

    /**
     * The store text for [notes] in the store locale [locale] (e.g. `de-DE`), resolved with [LocaleFallback].
     * Bullets are trimmed, empty ones skipped, and each written as `• <bullet>` on its own line. Over [limit]
     * characters (UTF-16 code units, never fewer than Play counts) the result is [StoreTextResult.TooLong]: the text
     * is never truncated, so the notes must be shortened.
     */
    public fun make(notes: ReleaseNotes, locale: String, limit: Int = PLAY_LIMIT): StoreTextResult {
        val text = if (notes.show) {
            render(notes.bullets(listOf(locale))).ifEmpty { return StoreTextResult.NoNotes }
        } else {
            FIX_ONLY_TEXT
        }
        return if (text.length > limit) StoreTextResult.TooLong(text.length) else StoreTextResult.Text(text)
    }

    /** [bullets] as store text: trimmed, empty ones skipped, `• <bullet>` joined with newlines. */
    internal fun render(bullets: List<String>): String =
        bullets.map { it.trimAsciiWhitespace() }.filter { it.isNotEmpty() }.joinToString("\n") { "• $it" }
}

/** Result of [StoreText.make]. */
public sealed interface StoreTextResult {
    /** Text ready to paste into the store field. */
    public data class Text(public val text: String) : StoreTextResult

    /** The text would be [length] characters, over the limit; shorten the notes. */
    public data class TooLong(public val length: Int) : StoreTextResult

    /** Neither the locale nor `en` has any non-empty bullet. */
    public data object NoNotes : StoreTextResult
}
