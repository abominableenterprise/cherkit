package com.abominableenterprise.cherkit.sunset

/** The platform whose block of the hosted config applies to this app build. */
public enum class SunsetPlatform(
    /** The key of this platform's block in the hosted JSON. */
    public val key: String,
) {
    /** The `"ios"` block: App Store builds. */
    IOS("ios"),

    /** The `"android"` block: Google Play builds. */
    ANDROID("android"),
}
