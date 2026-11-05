package com.abominableenterprise.cherkit.attribution.meta

import com.abominableenterprise.cherkit.attribution.revenuecat.Audience

/** Same launch policy as the RevenueCat adapter, with a bundled Meta configuration requirement. */
internal fun shouldEnableMetaAttribution(
    platform: String,
    audience: Audience,
    isDebug: Boolean,
    isTestHost: Boolean,
    isSimulator: Boolean,
    hasMetaConfiguration: Boolean,
): Boolean = platform in setOf("iOS", "android") && audience == Audience.AdultOnly &&
    !isDebug && !isTestHost && !isSimulator && hasMetaConfiguration
