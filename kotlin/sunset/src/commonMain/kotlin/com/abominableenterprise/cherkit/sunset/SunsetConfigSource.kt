package com.abominableenterprise.cherkit.sunset

import kotlin.time.Duration

/** Fetches the raw hosted config. [FetchSunsetConfigSource] wraps the app's HTTP call; apps can supply their own. */
public fun interface SunsetConfigSource {
    /**
     * Returns the response body, or `null` when the fetch fails, is not a success, or takes longer than [timeout].
     * Must not throw for network errors: Sunset fails open and falls back to the cache.
     */
    public suspend fun fetch(timeout: Duration): String?
}
