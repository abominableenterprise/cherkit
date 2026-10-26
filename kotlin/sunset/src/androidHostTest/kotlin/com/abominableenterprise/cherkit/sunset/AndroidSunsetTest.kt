package com.abominableenterprise.cherkit.sunset

import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The androidMain storage with a fake SharedPreferences: host tests run against android.jar stubs, so a real
 * SharedPreferences file, PackageManager or Build.VERSION is never exercised here.
 */
class AndroidSunsetTest {
    private val soft = """{"schemaVersion": 1, "android": {"latestVersion": "2.0", "storeURL": "https://play.google.com/store/apps/details?id=b"}}"""

    @Test
    fun sharedPreferencesCacheRoundTripsAndTreatsOtherTypesAsAbsent() = runBlocking<Unit> {
        val preferences = FakeSharedPreferences(mutableMapOf(KeyValueSunsetConfigCache.DEFAULT_KEY to 42))
        val cache = KeyValueSunsetConfigCache(SharedPreferencesSunsetStore(preferences))
        assertNull(cache.load())

        val checker = SunsetChecker(FetchSunsetConfigSource { soft }, cache)
        assertIs<SunsetDecision.SoftUpdate>(checker.check("1.0", "34", listOf("en")))
        assertEquals(soft, preferences.values[KeyValueSunsetConfigCache.DEFAULT_KEY])
        assertEquals(soft, cache.load())

        val offline = SunsetChecker(FetchSunsetConfigSource { null }, cache)
        assertIs<SunsetDecision.SoftUpdate>(offline.check("1.0", "34", listOf("en")))
    }

    @Test
    fun checkRunsTheCheckerForTheAppAndFailsOpenWithoutAVersionName() = runBlocking<Unit> {
        // android.jar stubs have no PackageManager, so versionName reads as "" and the decision fails open.
        val context = ContextWrapper(null)
        assertEquals("", AndroidSunset.installedVersion(context))
        val cache = MemoryCache()
        var fetches = 0
        val checker = SunsetChecker(FetchSunsetConfigSource { fetches++; soft }, cache)

        assertEquals(SunsetDecision.UpToDate, AndroidSunset.check(checker, context, listOf("en")))
        assertEquals(1, fetches)
        assertEquals(soft, cache.load())
    }

    @Test
    @Suppress("DEPRECATION")
    fun deprecatedExtensionStillDelegatesToAndroidSunsetCheck() = runBlocking<Unit> {
        var fetches = 0
        val checker = SunsetChecker(FetchSunsetConfigSource { fetches++; soft }, MemoryCache())
        assertEquals(SunsetDecision.UpToDate, checker.check(ContextWrapper(null), listOf("en")))
        assertEquals(1, fetches)
    }

    private class MemoryCache : SunsetConfigCache {
        private var saved: String? = null
        override suspend fun load(): String? = saved
        override suspend fun save(text: String) {
            saved = text
        }
    }

    /** Map-backed SharedPreferences whose getString throws ClassCastException for non-strings, like Android's. */
    private class FakeSharedPreferences(val values: MutableMap<String, Any?>) : SharedPreferences {
        override fun getString(key: String, defValue: String?): String? =
            if (key in values) values[key] as String? else defValue

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun getAll(): MutableMap<String, *> = values
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun contains(key: String): Boolean = key in values
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
            override fun apply() {
                values.putAll(pending)
            }
            override fun commit(): Boolean = true.also { apply() }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String): SharedPreferences.Editor = this
            override fun clear(): SharedPreferences.Editor = this
        }
    }
}
