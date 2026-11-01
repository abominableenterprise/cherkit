import CherKitCore
import Foundation

/// Decides between no prompt, a soft prompt and a hard block. Pure and fail-open.
public enum SunsetDecider {
    /// Decides for one launch.
    ///
    /// - `hardUpdate` when `appVersion` is below the block's `minimumVersion` (config only, on every channel; it always
    ///   wins);
    /// - else, on ``SunsetChannel/testFlight``, a soft ``SunsetUpdateKind/testBuild`` prompt when `installedBuild` is
    ///   below the block's `testFlight.latestBuild` and `now` is not before its `notBefore`;
    /// - else `softUpdate` when `appVersion` is below the newest offered version: the config's `latestVersion`, and on
    ///   ``SunsetChannel/store`` also `lookup.version` (the higher one wins);
    /// - else `upToDate`.
    ///
    /// Fails open (`upToDate`) when `appVersion` does not parse. When the block has a `latestMinimumOS` and `osVersion`
    /// is below it or does not parse, the config's versions give no prompt at all, not even a hard block: users on an
    /// old OS keep using the app they have rather than being locked out. The lookup's `minimumOsVersion` gates only the
    /// lookup's version the same way. A lookup prompt links the config's `storeURL`, else the lookup's `trackViewUrl`,
    /// and needs one of them. Test-build prompts are not gated by OS and never come from the lookup.
    ///
    /// - Parameters:
    ///   - config: The config to apply (see ``SunsetConfigChoice``), or `nil` when there is none.
    ///   - platform: Which block of the config applies.
    ///   - appVersion: The installed app version, e.g. `CFBundleShortVersionString` or `versionName`.
    ///   - osVersion: The device OS version: the iOS version (`"17.4.1"`) or the Android API level (`"34"`).
    ///   - languages: The user's preferred language tags in order, e.g. `Locale.preferredLanguages`.
    ///   - channel: How this build was installed. ``SunsetChannel/store`` keeps the behaviour from before channels.
    ///   - installedBuild: The installed build number (`CFBundleVersion`); needed only for test-build prompts.
    ///   - lookup: The App Store lookup result, or `nil` when the lookup is off or failed. Used only on
    ///     ``SunsetChannel/store``.
    ///   - now: The device clock, compared with `testFlight.notBefore`.
    public static func decide(
        config: SunsetConfig?,
        platform: SunsetPlatform,
        appVersion: String,
        osVersion: String,
        languages: [String],
        channel: SunsetChannel = .store,
        installedBuild: String? = nil,
        lookup: SunsetLookupResult? = nil,
        now: Date = Date()
    ) -> SunsetDecision {
        guard let installed = AppVersion(appVersion) else { return .upToDate }
        let inputs = SunsetDecisionInputs(
            block: config?.block(for: platform),
            installed: installed,
            os: AppVersion(osVersion),
            languages: languages,
            channel: channel,
            lookup: channel == .store ? lookup : nil
        )
        if let hard = inputs.hardUpdate() { return hard }
        if channel == .testFlight, let testBuild = inputs.testBuild(installedBuild: installedBuild, now: now) {
            return testBuild
        }
        return inputs.softUpdate() ?? .upToDate
    }
}
