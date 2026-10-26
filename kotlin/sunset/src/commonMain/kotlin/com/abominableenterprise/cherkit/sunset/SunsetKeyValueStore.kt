package com.abominableenterprise.cherkit.sunset

/**
 * The tiny string storage behind [KeyValueSunsetConfigCache], implemented by the app (on Android use
 * `SharedPreferencesSunsetStore`). Calls must be fast local storage.
 */
public interface SunsetKeyValueStore {
    /** The string under [key], or `null` when absent or not a string. */
    public fun getString(key: String): String?

    /** Stores [value] under [key]. */
    public fun putString(key: String, value: String)
}
