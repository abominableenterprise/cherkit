package com.abominableenterprise.cherkit.sunset

import android.content.SharedPreferences

/** [SunsetKeyValueStore] in Android [SharedPreferences]. A value of another type counts as absent. */
public class SharedPreferencesSunsetStore(private val preferences: SharedPreferences) : SunsetKeyValueStore {
    override fun getString(key: String): String? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}
