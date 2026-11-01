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
    fun historyMatchesTheCheckerAndWritesNothing() {
        val values = mutableMapOf(
            "cherkit.whatsnew.lastSeenVersion" to "1.0.0",
            "cherkit.whatsnew.installHistory" to """["1.0.0","1.1.0"]""",
        )
        val writes = mutableListOf<String>()
        val storage = object : KeyValueStorage {
            override fun getString(key: String): String? = values[key]
            override fun putString(key: String, value: String) {
                writes += key
                values[key] = value
            }
        }
        val source = LoadedReleaseNotesSource {
            listOf(
                "1.0.0.json" to """{"version":"1.0.0","notes":{"en":["First release."]}}""",
                "1.1.0.json" to """{"version":"1.1.0","date":"2026-11-02","notes":{"en":["CSV export."],"de":["CSV."]}}""",
                "1.2.0.json" to """{"version":"1.2.0","notes":{"en":["Widgets."]}}""",
            )
        }
        val store = KeyValueWhatsNewStore(storage)
        val locales = listOf("de-DE", "en-US")

        val history = AndroidWhatsNew.history(source, store, locales)
        assertEquals(WhatsNewChecker(source, store).history(locales), history)
        assertEquals(listOf("1.2.0", "1.1.0", "1.0.0"), history.map { it.version.toString() })
        assertEquals(listOf("CSV."), history[1].bullets)
        assertEquals(listOf(false, true, false), history.map { it.isInstalled })
        // Read-only: no install-history append, nothing marked seen.
        assertEquals(emptyList(), writes)
        assertEquals("1.0.0", values["cherkit.whatsnew.lastSeenVersion"])
        assertEquals("""["1.0.0","1.1.0"]""", values["cherkit.whatsnew.installHistory"])
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
