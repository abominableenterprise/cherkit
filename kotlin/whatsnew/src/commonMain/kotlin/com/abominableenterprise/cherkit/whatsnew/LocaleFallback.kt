package com.abominableenterprise.cherkit.whatsnew

/**
 * Picks which notes locale to show: `pt-BR` -> `pt` -> (next preferred locale) -> `en`.
 * The contract is `fixtures/whatsnew/locale.json`.
 */
public object LocaleFallback {
    /** Locale used when none of the preferred ones match. */
    public const val DEFAULT_LOCALE: String = "en"

    /**
     * The key of [available] to use for [preferred] (most preferred first). Each preferred locale is tried, then
     * shortened by dropping trailing subtags; [DEFAULT_LOCALE] is tried last. Matching ignores ASCII case and treats
     * `_` like `-`. Returns the key as spelled in [available], or `null`.
     */
    public fun resolve(preferred: List<String>, available: Collection<String>): String? {
        val byNormalized = mutableMapOf<String, String>()
        for (key in available.sorted()) byNormalized.getOrPut(normalize(key)) { key }
        for (locale in preferred + DEFAULT_LOCALE) {
            val tags = normalize(locale).split('-').toMutableList()
            while (tags.isNotEmpty() && !(tags.size == 1 && tags[0].isEmpty())) {
                byNormalized[tags.joinToString("-")]?.let { return it }
                tags.removeAt(tags.lastIndex)
            }
        }
        return null
    }

    private fun normalize(locale: String): String =
        locale.trimAsciiWhitespace().map { if (it == '_') '-' else if (it in 'A'..'Z') it + 32 else it }.joinToString("")
}
