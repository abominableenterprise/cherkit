package com.abominableenterprise.cherkit.sunset

import kotlin.time.Instant

/**
 * The optional `testFlight` object in the `"ios"` block: the newest build the release pipeline uploaded to TestFlight.
 * [SunsetConfig.parse] validates hosted values; the constructor does not.
 */
public data class SunsetTestFlightConfig(
    /** TestFlight installs whose `CFBundleVersion` is below this get a soft [SunsetUpdateKind.TEST_BUILD] prompt. */
    public val latestBuild: SunsetBuildNumber,
    /** The link to open, `itms-beta://` or `https://` (any host); `null` when absent or invalid. */
    public val url: String? = null,
    /** No prompt while the device clock is before this; `null` when absent or malformed (no delay). */
    public val notBefore: Instant? = null,
    /** Custom prompt text by language tag, resolved like the block's `message`. */
    public val messages: Map<String, String> = emptyMap(),
) {
    public companion object {
        /** What a test-build prompt opens when the config has no [url]: the TestFlight app. */
        public const val DEFAULT_URL: String = "itms-beta://"
    }
}
