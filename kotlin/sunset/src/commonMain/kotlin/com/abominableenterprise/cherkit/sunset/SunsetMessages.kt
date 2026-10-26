package com.abominableenterprise.cherkit.sunset

/** Picks the custom prompt text for the user's languages. */
public object SunsetMessages {
    /** The language used when none of the user's languages has a message. */
    public const val FALLBACK_LANGUAGE: String = "en"

    /**
     * The first message matching, in order: each preferred tag, then that tag with trailing subtags removed one at a
     * time (`de-DE` -> `de`), then [FALLBACK_LANGUAGE]. `null` when none matches: the UI shows its default text.
     *
     * Tags and keys match case-insensitively (ASCII), with `_` treated as `-` (so `Locale.toString()`'s `de_DE`
     * works). Empty texts, empty tags, and tags or keys with non-ASCII characters are skipped. When two keys are the
     * same after that (`de` and `DE`), the one sorting first wins.
     */
    public fun resolve(messages: Map<String, String>, languages: List<String>): String? {
        val byTag = mutableMapOf<String, String>()
        // Keys are ASCII here, so Swift's and Kotlin's sort orders agree on the winner.
        for (key in messages.keys.filter(::isUsableTag).sorted()) {
            val text = messages.getValue(key)
            if (text.isEmpty()) continue
            byTag.getOrPut(normalize(key)) { text }
        }
        for (language in languages.filter(::isUsableTag)) {
            val subtags = normalize(language).split('-').toMutableList()
            while (subtags.isNotEmpty()) {
                val tag = subtags.joinToString("-")
                if (tag.isNotEmpty()) byTag[tag]?.let { return it }
                subtags.removeAt(subtags.lastIndex)
            }
        }
        return byTag[FALLBACK_LANGUAGE]
    }

    /** Non-empty and ASCII only, as BCP 47 tags are; Swift and Kotlin compare non-ASCII strings differently. */
    private fun isUsableTag(tag: String): Boolean = tag.isNotEmpty() && tag.all { it.code < 0x80 }

    /** Lowercased with `_` replaced by `-`; only called on ASCII tags. */
    private fun normalize(tag: String): String = tag.lowercase().replace('_', '-')
}
