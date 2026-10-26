package com.abominableenterprise.cherkit.sunset

import kotlin.time.Duration

/** The source of [SunsetChecker.disabled]: never fetches. */
internal object DisabledSunsetConfigSource : SunsetConfigSource {
    override suspend fun fetch(timeout: Duration): String? = null
}

/** The cache of [SunsetChecker.disabled]: stores nothing. */
internal object DisabledSunsetConfigCache : SunsetConfigCache {
    override suspend fun load(): String? = null

    override suspend fun save(text: String) {}
}
