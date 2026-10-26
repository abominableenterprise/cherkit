package com.abominableenterprise.cherkit.sunset

/** Which config to apply: a fresh fetch, else the cached last good config, else none. */
public data class SunsetConfigChoice(
    /** Where [config] came from. */
    public val source: Source,
    /** The config to pass to [SunsetDecider.decide]. */
    public val config: SunsetConfig?,
) {
    /** Where a chosen config came from. */
    public enum class Source {
        /** The fetched body parsed: apply it and save it with [SunsetConfigCache.save]. */
        FETCHED,

        /** The fetch failed or was unusable; the cached last good config parsed. */
        CACHED,

        /** Neither parsed: [config] is `null` and the decision is `UpToDate` (fail open). */
        MISSING,
    }

    public companion object {
        /**
         * Chooses between [fetched] (the body just downloaded, `null` on failure or timeout) and [cached] (the last
         * good body, `null` when none). An unusable fetch never wins, so only [Source.FETCHED] bodies should be cached.
         */
        public fun choose(fetched: String?, cached: String?): SunsetConfigChoice {
            fetched?.let(SunsetConfig::parse)?.let { return SunsetConfigChoice(Source.FETCHED, it) }
            cached?.let(SunsetConfig::parse)?.let { return SunsetConfigChoice(Source.CACHED, it) }
            return SunsetConfigChoice(Source.MISSING, null)
        }
    }
}
