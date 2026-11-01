package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion
import kotlin.time.Clock
import kotlin.time.Instant

/** Decides between no prompt, a soft prompt and a hard block. Pure and fail-open. */
public object SunsetDecider {
    /**
     * Decides for one launch:
     * - [SunsetDecision.HardUpdate] when [appVersion] is below the block's `minimumVersion` (config only, on every
     *   channel; it always wins);
     * - else, on [SunsetChannel.TEST_FLIGHT], a soft [SunsetUpdateKind.TEST_BUILD] prompt when [installedBuild] is below
     *   the block's `testFlight.latestBuild` and [now] is not before its `notBefore`;
     * - else [SunsetDecision.SoftUpdate] when [appVersion] is below the newest offered version: the config's
     *   `latestVersion`, and on [SunsetChannel.STORE] also [lookup]'s version (the higher one wins);
     * - else [SunsetDecision.UpToDate].
     *
     * Fails open (`UpToDate`) when [appVersion] does not parse. When the block has a `latestMinimumOS` and [osVersion]
     * is below it or does not parse, the config's versions give no prompt at all, not even a hard block: users on an
     * old OS keep using the app they have rather than being locked out. The lookup's `minimumOsVersion` gates only the
     * lookup's version the same way. A lookup prompt links the config's `storeURL`, else the lookup's `trackViewUrl`,
     * and needs one of them. Test-build prompts are not gated by OS and never come from the lookup.
     *
     * @param config The config to apply (see [SunsetConfigChoice]), or `null` when there is none.
     * @param platform Which block of the config applies.
     * @param appVersion The installed app version, e.g. `versionName` or `CFBundleShortVersionString`.
     * @param osVersion The device OS version: the Android API level (`"34"`) or the iOS version (`"17.4.1"`).
     * @param languages The user's preferred language tags in order, e.g. from `LocaleListCompat`.
     * @param channel How this build was installed. [SunsetChannel.STORE] keeps the behaviour from before channels.
     * @param installedBuild The installed build number (`CFBundleVersion`); needed only for test-build prompts.
     * @param lookup The App Store lookup result, or `null` when the lookup is off or failed. Used only on
     *   [SunsetChannel.STORE].
     * @param now The device clock, compared with `testFlight.notBefore`.
     */
    public fun decide(
        config: SunsetConfig?,
        platform: SunsetPlatform,
        appVersion: String,
        osVersion: String,
        languages: List<String>,
        channel: SunsetChannel = SunsetChannel.STORE,
        installedBuild: String? = null,
        lookup: SunsetLookupResult? = null,
        now: Instant = Clock.System.now(),
    ): SunsetDecision {
        val installed = AppVersion.parse(appVersion) ?: return SunsetDecision.UpToDate
        val inputs = SunsetDecisionInputs(
            block = config?.block(platform),
            installed = installed,
            os = AppVersion.parse(osVersion),
            languages = languages,
            lookup = lookup.takeIf { channel == SunsetChannel.STORE },
        )
        inputs.hardUpdate()?.let { return it }
        if (channel == SunsetChannel.TEST_FLIGHT) inputs.testBuild(installedBuild, now)?.let { return it }
        return inputs.softUpdate() ?: SunsetDecision.UpToDate
    }

    /** The 0.1.0 signature, kept so code compiled against it still links. Source calls resolve to the one above. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public fun decide(
        config: SunsetConfig?,
        platform: SunsetPlatform,
        appVersion: String,
        osVersion: String,
        languages: List<String>,
    ): SunsetDecision = decide(config, platform, appVersion, osVersion, languages, SunsetChannel.STORE)
}
