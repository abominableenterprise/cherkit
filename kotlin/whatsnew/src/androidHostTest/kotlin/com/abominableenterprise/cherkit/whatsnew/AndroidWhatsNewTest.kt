package com.abominableenterprise.cherkit.whatsnew

import android.content.SharedPreferences
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The androidMain helpers with fakes: host tests run against android.jar stubs, so a real AssetManager, Context or
 * SharedPreferences file is never exercised here.
 */
class AndroidWhatsNewTest {
    @Test
    fun readAssetsReadsOnlyJsonFilesInTheFolder() {
        val assets = mapOf(
            "whatsnew/1.1.0.json" to """{ "version": "1.1.0", "notes": { "en": ["Dark mode."] } }""",
            "whatsnew/README.txt" to "notes",
        )
        val opened = mutableListOf<String>()
        val files = AndroidWhatsNew.readAssets(
            "whatsnew",
            list = { dir -> if (dir == "whatsnew") arrayOf("1.1.0.json", "README.txt", "broken.json") else null },
            open = { path ->
                opened += path
                ByteArrayInputStream((assets[path] ?: throw FileNotFoundException(path)).encodeToByteArray())
            },
        )
        assertEquals(listOf("1.1.0.json" to assets.getValue("whatsnew/1.1.0.json"), "broken.json" to ""), files)
        assertEquals(listOf("whatsnew/1.1.0.json", "whatsnew/broken.json"), opened)
        assertEquals(emptyList(), AndroidWhatsNew.readAssets("missing", { null }, { error("not called") }))
    }

    @Test
    fun sharedPreferencesStorageRoundTripsAndTreatsOtherTypesAsAbsent() {
        val preferences = FakeSharedPreferences(mutableMapOf("cherkit.whatsnew.lastSeenVersion" to 42))
        val store = KeyValueWhatsNewStore(SharedPreferencesKeyValueStorage(preferences))
        assertNull(store.lastSeenVersion())
        store.setLastSeenVersion("1.2.0")
        store.setInstallHistory(listOf("1.0.0", "1.2.0"))
        assertEquals("1.2.0", store.lastSeenVersion())
        assertEquals(listOf("1.0.0", "1.2.0"), store.installHistory())
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
