package com.abominableenterprise.cherkit.whatsnew

/**
 * Where WhatsNew keeps its per-device state. UserDefaults and SharedPreferences adapters come in a later PR; tests
 * and apps can use any implementation. Calls happen on the caller's thread and must be fast (local storage).
 */
public interface WhatsNewStore {
    /** The version whose notes the user last saw, or `null` on first launch. */
    public fun lastSeenVersion(): String?

    /** Stores the version from [LaunchPlan.lastSeen]. */
    public fun setLastSeenVersion(version: String)

    /** The stored install history, oldest first (see [InstallHistory]). */
    public fun installHistory(): List<String>

    /** Replaces the stored install history. */
    public fun setInstallHistory(history: List<String>)
}
