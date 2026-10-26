package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion

/** Decides between no prompt, a soft prompt and a hard block. Pure and fail-open. */
public object SunsetDecider {
    /**
     * Decides for one launch: [SunsetDecision.HardUpdate] when [appVersion] is below the block's `minimumVersion`,
     * else [SunsetDecision.SoftUpdate] when it is below `latestVersion`, else [SunsetDecision.UpToDate].
     *
     * Fails open (`UpToDate`) when [config] is `null`, has no block for [platform], or [appVersion] does not parse.
     * When the block has a `latestMinimumOS` and [osVersion] is below it or does not parse, the device cannot install
     * the update, so there is no prompt at all, not even a hard block: users on an old OS keep using the app they
     * have rather than being locked out with no way forward.
     *
     * @param config The config to apply (see [SunsetConfigChoice]), or `null` when there is none.
     * @param platform Which block of the config applies.
     * @param appVersion The installed app version, e.g. `versionName` or `CFBundleShortVersionString`.
     * @param osVersion The device OS version: the Android API level (`"34"`) or the iOS version (`"17.4.1"`).
     * @param languages The user's preferred language tags in order, e.g. from `LocaleListCompat`.
     */
    public fun decide(
        config: SunsetConfig?,
        platform: SunsetPlatform,
        appVersion: String,
        osVersion: String,
        languages: List<String>,
    ): SunsetDecision {
        val block = config?.block(platform) ?: return SunsetDecision.UpToDate
        val installed = AppVersion.parse(appVersion) ?: return SunsetDecision.UpToDate
        block.latestMinimumOS?.let { minimumOS ->
            val os = AppVersion.parse(osVersion)
            if (os == null || os < minimumOS) return SunsetDecision.UpToDate
        }
        val prompt = SunsetPrompt(block.storeUrl, SunsetMessages.resolve(block.messages, languages))
        block.minimumVersion?.let { if (installed < it) return SunsetDecision.HardUpdate(prompt) }
        block.latestVersion?.let { if (installed < it) return SunsetDecision.SoftUpdate(prompt) }
        return SunsetDecision.UpToDate
    }
}
