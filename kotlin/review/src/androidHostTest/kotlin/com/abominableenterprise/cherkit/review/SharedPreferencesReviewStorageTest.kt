package com.abominableenterprise.cherkit.review

import android.content.SharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The SharedPreferences store with a fake: host tests run against android.jar stubs. */
class SharedPreferencesReviewStorageTest {
    @Test
    fun roundTripsUnderTheDefaultPrefix() {
        val preferences = FakeSharedPreferences(mutableMapOf())
        val store = KeyValueReviewStore(SharedPreferencesReviewStorage(preferences))
        assertEquals(ReviewState(), store.load())

        store.save(ReviewState(successCount = 3, isDue = true, lastPromptedVersion = "1.2"))
        assertEquals(ReviewState(3, true, "1.2"), store.load())
        assertEquals(
            mapOf<String, Any?>(
                "cherkit.review.successCount" to 3,
                "cherkit.review.isDue" to true,
                "cherkit.review.lastPromptedVersion" to "1.2",
            ),
            preferences.values,
        )

        store.save(ReviewState())
        assertFalse("cherkit.review.lastPromptedVersion" in preferences.values)
    }

    @Test
    fun treatsOtherTypesAsAbsent() {
        val preferences = FakeSharedPreferences(
            mutableMapOf("t.successCount" to "5", "t.isDue" to 1, "t.lastPromptedVersion" to 12L),
        )
        assertEquals(ReviewState(), KeyValueReviewStore(SharedPreferencesReviewStorage(preferences), "t.").load())
    }

    /** Map-backed SharedPreferences whose typed getters throw ClassCastException for another type, like Android's. */
    private class FakeSharedPreferences(val values: MutableMap<String, Any?>) : SharedPreferences {
        override fun getString(key: String, defValue: String?): String? =
            if (key in values) values[key] as String? else defValue
        override fun getInt(key: String, defValue: Int): Int = if (key in values) values[key] as Int else defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            if (key in values) values[key] as Boolean else defValue
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun getAll(): MutableMap<String, *> = values
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun contains(key: String): Boolean = key in values
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { removed += key }
            override fun apply() {
                removed.forEach(values::remove)
                values.putAll(pending)
            }
            override fun commit(): Boolean = true.also { apply() }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
            override fun clear(): SharedPreferences.Editor = this
        }
    }
}
