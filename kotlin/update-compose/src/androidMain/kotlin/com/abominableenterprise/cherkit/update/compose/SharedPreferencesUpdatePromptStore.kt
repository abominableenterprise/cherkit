package com.abominableenterprise.cherkit.update.compose

import android.content.SharedPreferences

/** An [UpdatePromptStore] keeping the declined versionCode as one int under [key] in [prefs]. */
public class SharedPreferencesUpdatePromptStore(
    /** Where the declined versionCode is stored. */
    public val prefs: SharedPreferences,
    /** The key it is stored under. */
    public val key: String = DEFAULT_KEY,
) : UpdatePromptStore {
    override val declinedVersionCode: Int?
        get() = try {
            if (prefs.contains(key)) prefs.getInt(key, 0) else null
        } catch (_: ClassCastException) {
            null
        }

    override fun rememberDeclined(versionCode: Int) {
        prefs.edit().putInt(key, versionCode).apply()
    }

    /** Forgets any "Not now", e.g. from a debug menu. */
    public fun reset() {
        prefs.edit().remove(key).apply()
    }

    public companion object {
        /** The key used unless you pass another. */
        public const val DEFAULT_KEY: String = "cherkit.update.declinedVersionCode"
    }
}
