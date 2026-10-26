package com.abominableenterprise.cherkit.whatsnew

/**
 * The tiny string key-value storage behind [KeyValueWhatsNewStore]. On Android use `SharedPreferencesKeyValueStorage`.
 * Calls happen on the caller's thread and must be fast (local storage).
 */
public interface KeyValueStorage {
    /** The string stored under [key], or `null` when absent or not readable as a string. Must not throw. */
    public fun getString(key: String): String?

    /** Stores [value] under [key]. */
    public fun putString(key: String, value: String)
}
