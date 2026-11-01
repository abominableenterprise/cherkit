package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion
import kotlin.time.Instant

/** One launch's inputs to [SunsetDecider], with each rule as its own small step. */
internal class SunsetDecisionInputs(
    private val block: SunsetPlatformConfig?,
    private val installed: AppVersion,
    private val os: AppVersion?,
    private val languages: List<String>,
    /** `null` unless the channel is [SunsetChannel.STORE]. */
    lookup: SunsetLookupResult?,
) {
    /** The block, when its `latestMinimumOS` (if any) lets this device install its versions. */
    private val usableBlock: SunsetPlatformConfig? = block?.takeIf { b ->
        val minimumOS = b.latestMinimumOS
        minimumOS == null || (os != null && os >= minimumOS)
    }

    /** The lookup, when the device meets its `minimumOsVersion` and some https link can open the store. */
    private val usableLookup: SunsetLookupResult? = lookup?.takeIf {
        os != null && os >= it.minimumOsVersion && (block?.storeUrl ?: it.trackViewUrl) != null
    }

    /** The newest release version offered: the higher of the usable config `latestVersion` and lookup version. */
    private val latestVersion: AppVersion? = listOfNotNull(usableBlock?.latestVersion, usableLookup?.version).maxOrNull()

    private fun updatePrompt(): SunsetPrompt? {
        val url = block?.storeUrl ?: usableLookup?.trackViewUrl ?: return null
        val message = SunsetMessages.resolve(block?.messages.orEmpty(), languages)
        return SunsetPrompt(url, message, latestVersion)
    }

    /** A hard block from the config's `minimumVersion` alone. */
    fun hardUpdate(): SunsetDecision? {
        val minimum = usableBlock?.minimumVersion ?: return null
        if (installed >= minimum) return null
        return updatePrompt()?.let { SunsetDecision.HardUpdate(it) }
    }

    /** A soft release prompt from the config's or the lookup's version. */
    fun softUpdate(): SunsetDecision? {
        val latest = latestVersion ?: return null
        if (installed >= latest) return null
        return updatePrompt()?.let { SunsetDecision.SoftUpdate(it) }
    }

    /** A soft test-build prompt from the block's `testFlight` object. */
    fun testBuild(installedBuild: String?, now: Instant): SunsetDecision? {
        val testFlight = block?.testFlight ?: return null
        val build = installedBuild?.let(SunsetBuildNumber::parse) ?: return null
        if (build >= testFlight.latestBuild) return null
        testFlight.notBefore?.let { if (now < it) return null }
        val prompt = SunsetPrompt(
            storeUrl = testFlight.url ?: SunsetTestFlightConfig.DEFAULT_URL,
            message = SunsetMessages.resolve(testFlight.messages, languages),
            kind = SunsetUpdateKind.TEST_BUILD,
            latestBuild = testFlight.latestBuild,
        )
        return SunsetDecision.SoftUpdate(prompt)
    }
}
