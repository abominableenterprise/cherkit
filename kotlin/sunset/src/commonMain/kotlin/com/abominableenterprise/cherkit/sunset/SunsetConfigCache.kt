package com.abominableenterprise.cherkit.sunset

/** Stores the last good config body so Sunset still works offline. [KeyValueSunsetConfigCache] is the built-in one. */
public interface SunsetConfigCache {
    /** The last saved body, or `null` when there is none or it cannot be read. */
    public suspend fun load(): String?

    /** Saves [text]. Called only with bodies that parsed ([SunsetConfigChoice.Source.FETCHED]). Failures are ignored. */
    public suspend fun save(text: String)
}
