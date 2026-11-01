package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion

/** What an update prompt shows and where it links. */
public data class SunsetPrompt(
    /**
     * The page to open: the config's `storeURL` (else the App Store lookup's `trackViewUrl`), or for a
     * [SunsetUpdateKind.TEST_BUILD] prompt the `testFlight.url` (else [SunsetTestFlightConfig.DEFAULT_URL]).
     */
    public val storeUrl: String,
    /** Custom text in the user's language, or `null` when the config has none that fits: the UI uses its default. */
    public val message: String?,
    /**
     * The newest release version offered (the higher of the config's `latestVersion` and the App Store lookup's), or
     * `null` when there is none (e.g. a hard block from `minimumVersion` alone, or a test-build prompt). The UI
     * remembers "Not now" for this version.
     */
    public val latestVersion: AppVersion? = null,
    /** What the prompt offers. [SunsetUpdateKind.UPDATE] unless it is a TestFlight test build. */
    public val kind: SunsetUpdateKind = SunsetUpdateKind.UPDATE,
    /** For a [SunsetUpdateKind.TEST_BUILD] prompt, the config's `testFlight.latestBuild`; else `null`. */
    public val latestBuild: SunsetBuildNumber? = null,
) {
    /** The 0.2.0 constructor, kept so code compiled against it still links. Source calls resolve to the one above. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(storeUrl: String, message: String?, latestVersion: AppVersion? = null) :
        this(storeUrl, message, latestVersion, SunsetUpdateKind.UPDATE, null)

    /** The 0.2.0 `copy`, kept so code compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public fun copy(
        storeUrl: String = this.storeUrl,
        message: String? = this.message,
        latestVersion: AppVersion? = this.latestVersion,
    ): SunsetPrompt = SunsetPrompt(storeUrl, message, latestVersion, kind, latestBuild)
}
