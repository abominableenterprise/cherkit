package com.abominableenterprise.cherkit.review

import android.content.SharedPreferences

/**
 * [ReviewKeyValueStorage] in Android [SharedPreferences]. A value of another type counts as absent; writes go in one
 * `apply()`.
 */
public class SharedPreferencesReviewStorage(private val preferences: SharedPreferences) : ReviewKeyValueStorage {
    override fun getInt(key: String): Int? = typed(key) { preferences.getInt(key, 0) }

    override fun getBoolean(key: String): Boolean? = typed(key) { preferences.getBoolean(key, false) }

    override fun getString(key: String): String? = typed(key) { preferences.getString(key, null) }

    override fun put(values: Map<String, Any?>) {
        val editor = preferences.edit()
        for ((key, value) in values) {
            when (value) {
                null -> editor.remove(key)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                else -> throw IllegalArgumentException("unsupported value type for $key")
            }
        }
        editor.apply()
    }

    /** Android's typed getters throw ClassCastException for a value of another type. */
    private inline fun <T> typed(key: String, read: () -> T): T? = try {
        if (preferences.contains(key)) read() else null
    } catch (_: ClassCastException) {
        null
    }
}
