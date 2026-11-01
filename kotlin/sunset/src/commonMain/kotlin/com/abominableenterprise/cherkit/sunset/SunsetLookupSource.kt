package com.abominableenterprise.cherkit.sunset

import kotlin.time.Duration

/**
 * Fetches the raw App Store lookup response (`https://itunes.apple.com/lookup?bundleId=<id>&country=<cc>`). Opt in by
 * passing one to [SunsetChecker]; it is only asked on the [SunsetChannel.STORE] channel. Android apps do not need it.
 */
public fun interface SunsetLookupSource {
    /**
     * Returns the response body, or `null` when the fetch fails, is not a success, or takes longer than [timeout].
     * Must not throw for network errors: a failed lookup means no lookup prompt.
     */
    public suspend fun fetch(timeout: Duration): String?
}
