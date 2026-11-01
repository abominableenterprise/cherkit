package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion

/** One platform's block of the hosted config. [SunsetConfig.parse] validates hosted values; the constructor does not. */
public data class SunsetPlatformConfig(
    /** Builds below this get a hard (blocking) update prompt. `null`: never hard. */
    public val minimumVersion: AppVersion? = null,
    /** Builds below this get a soft (dismissible) update prompt. `null`: never soft. */
    public val latestVersion: AppVersion? = null,
    /**
     * Lowest OS version (Android API level, or iOS version) that can install [latestVersion]. Devices below it are
     * never prompted, not even for a hard update, because they cannot install the update. `null`: OS is not checked.
     */
    public val latestMinimumOS: AppVersion? = null,
    /** Where the update prompt sends the user (the JSON `storeURL`). Always `https` in parsed configs. */
    public val storeUrl: String,
    /** Custom prompt text by language tag (`"en"`, `"de"`, `"pt-BR"`); resolve it with [SunsetMessages.resolve]. */
    public val messages: Map<String, String> = emptyMap(),
    /** The optional `testFlight` object (iOS): the newest TestFlight build. `null` when absent or invalid. */
    public val testFlight: SunsetTestFlightConfig? = null,
) {
    /** The 0.1.0 constructor, kept so code compiled against it still links. Source calls resolve to the one above. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(
        minimumVersion: AppVersion? = null,
        latestVersion: AppVersion? = null,
        latestMinimumOS: AppVersion? = null,
        storeUrl: String,
        messages: Map<String, String> = emptyMap(),
    ) : this(minimumVersion, latestVersion, latestMinimumOS, storeUrl, messages, null)

    /** The 0.1.0 `copy`, kept so code compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public fun copy(
        minimumVersion: AppVersion? = this.minimumVersion,
        latestVersion: AppVersion? = this.latestVersion,
        latestMinimumOS: AppVersion? = this.latestMinimumOS,
        storeUrl: String = this.storeUrl,
        messages: Map<String, String> = this.messages,
    ): SunsetPlatformConfig = SunsetPlatformConfig(minimumVersion, latestVersion, latestMinimumOS, storeUrl, messages, testFlight)
}
