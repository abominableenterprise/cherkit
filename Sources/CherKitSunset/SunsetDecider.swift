import CherKitCore

/// Decides between no prompt, a soft prompt and a hard block. Pure and fail-open.
public enum SunsetDecider {
    /// Decides for one launch.
    ///
    /// - `hardUpdate` when `appVersion` is below the block's `minimumVersion`;
    /// - else `softUpdate` when it is below `latestVersion`;
    /// - else `upToDate`.
    ///
    /// Fails open (`upToDate`) when `config` is `nil`, has no block for `platform`, or `appVersion` does not parse.
    /// When the block has a `latestMinimumOS` and `osVersion` is below it or does not parse, the device cannot install
    /// the update, so there is no prompt at all, not even a hard block: users on an old OS keep using the app they have
    /// rather than being locked out with no way forward.
    ///
    /// - Parameters:
    ///   - config: The config to apply (see ``SunsetConfigChoice``), or `nil` when there is none.
    ///   - platform: Which block of the config applies.
    ///   - appVersion: The installed app version, e.g. `CFBundleShortVersionString` or `versionName`.
    ///   - osVersion: The device OS version: the iOS version (`"17.4.1"`) or the Android API level (`"34"`).
    ///   - languages: The user's preferred language tags in order, e.g. `Locale.preferredLanguages`.
    public static func decide(
        config: SunsetConfig?,
        platform: SunsetPlatform,
        appVersion: String,
        osVersion: String,
        languages: [String]
    ) -> SunsetDecision {
        guard let block = config?.block(for: platform), let installed = AppVersion(appVersion) else { return .upToDate }
        if let minimumOS = block.latestMinimumOS {
            guard let os = AppVersion(osVersion), !(os < minimumOS) else { return .upToDate }
        }
        let prompt = SunsetPrompt(storeURL: block.storeURL, message: SunsetMessages.resolve(block.messages, languages: languages))
        if let minimum = block.minimumVersion, installed < minimum { return .hardUpdate(prompt) }
        if let latest = block.latestVersion, installed < latest { return .softUpdate(prompt) }
        return .upToDate
    }
}
