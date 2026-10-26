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
)
