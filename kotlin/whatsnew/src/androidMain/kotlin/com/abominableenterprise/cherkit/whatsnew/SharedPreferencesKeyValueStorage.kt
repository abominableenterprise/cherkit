package com.abominableenterprise.cherkit.whatsnew

import android.content.SharedPreferences

/** [KeyValueStorage] in Android [SharedPreferences]. A value of another type counts as absent; writes use `apply()`. */
public class SharedPreferencesKeyValueStorage(private val preferences: SharedPreferences) : KeyValueStorage {
    override fun getString(key: String): String? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}
