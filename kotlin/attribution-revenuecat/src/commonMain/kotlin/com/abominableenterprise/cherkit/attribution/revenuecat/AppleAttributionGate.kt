package com.abominableenterprise.cherkit.attribution.revenuecat

internal enum class AttributionPlatform { IOS, Android, MacOS }

/** Shared policy only. Kotlin has no Apple SDK route, even when this predicate returns true. */
internal fun shouldEnableAppleAttribution(
    platform: AttributionPlatform,
    audience: Audience,
    isDebug: Boolean,
    isTestHost: Boolean,
    isSimulator: Boolean,
): Boolean = platform == AttributionPlatform.IOS && audience == Audience.AdultOnly &&
    !isDebug && !isTestHost && !isSimulator
