package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion

/** What an update prompt shows and where it links. */
public data class SunsetPrompt(
    /** The store page to open, from the config's `storeURL`. */
    public val storeUrl: String,
    /** Custom text in the user's language, or `null` when the config has none that fits: the UI uses its default. */
    public val message: String?,
    /**
     * The config's `latestVersion` for this platform, or `null` when the config has none (e.g. a hard block from
     * `minimumVersion` alone). The UI remembers "Not now" for this version.
     */
    public val latestVersion: AppVersion? = null,
)
