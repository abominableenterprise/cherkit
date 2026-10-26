package com.abominableenterprise.cherkit.sunset

/** What an update prompt shows and where it links. */
public data class SunsetPrompt(
    /** The store page to open, from the config's `storeURL`. */
    public val storeUrl: String,
    /** Custom text in the user's language, or `null` when the config has none that fits: the UI uses its default. */
    public val message: String?,
)
