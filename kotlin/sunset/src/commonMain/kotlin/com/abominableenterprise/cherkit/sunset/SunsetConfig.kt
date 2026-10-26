package com.abominableenterprise.cherkit.sunset

/**
 * A parsed hosted Sunset config: one optional block per platform.
 *
 * The hosted file is small public JSON, one per app:
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "ios": { "minimumVersion": "1.2.0", "latestVersion": "1.4.0", "latestMinimumOS": "17.0",
 *            "storeURL": "https://apps.apple.com/app/id123", "message": { "en": "Please update." } },
 *   "android": { "minimumVersion": "1.2.0", "latestVersion": "1.4.0", "latestMinimumOS": "26",
 *                "storeURL": "https://play.google.com/store/apps/details?id=com.example" }
 * }
 * ```
 * Parsing fails open: see [parse]. The contract is `fixtures/sunset/`, shared with Swift's `SunsetConfig`.
 */
public data class SunsetConfig(
    /** The `"ios"` block, or `null` when it is missing or invalid. */
    public val ios: SunsetPlatformConfig? = null,
    /** The `"android"` block, or `null` when it is missing or invalid. */
    public val android: SunsetPlatformConfig? = null,
) {
    /** The block for [platform], or `null` when the config has none. */
    public fun block(platform: SunsetPlatform): SunsetPlatformConfig? = when (platform) {
        SunsetPlatform.IOS -> ios
        SunsetPlatform.ANDROID -> android
    }

    public companion object {
        /** The only `schemaVersion` this library understands. Any other value makes [parse] return `null`. */
        public const val SUPPORTED_SCHEMA_VERSION: Int = 1

        /**
         * Parses the hosted JSON, or returns `null` when it is not a usable config (callers then fail open).
         *
         * `null` for anything that is not a JSON object with `schemaVersion` equal to [SUPPORTED_SCHEMA_VERSION].
         * Unknown fields and platforms are ignored. A platform block that is not an object, has a version that is not
         * a valid `AppVersion` string, or lacks a valid `https` `storeURL` is dropped (that platform never prompts);
         * the other block is kept. A malformed `message` only drops the custom text.
         */
        public fun parse(text: String): SunsetConfig? = SunsetConfigParser.parse(text)
    }
}
