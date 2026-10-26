package com.abominableenterprise.cherkit.whatsnew

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Store and source behavior beyond `fixtures/whatsnew/checker.json`: corrupt stored values and throwing plug-ins. */
class WhatsNewCheckerTest {
    private val notes = listOf("1.1.0.json" to """{ "version": "1.1.0", "notes": { "en": ["Dark mode."] } }""")

    @Test
    fun keysUseThePrefix() {
        val storage = MapKeyValueStorage()
        val store = KeyValueWhatsNewStore(storage, keyPrefix = "app.")
        store.setLastSeenVersion("1.2.0")
        store.setInstallHistory(listOf("1.0.0", "1.2.0"))
        assertEquals(mapOf("app.lastSeenVersion" to "1.2.0", "app.installHistory" to """["1.0.0","1.2.0"]"""), storage.values)
        assertEquals(listOf("1.0.0", "1.2.0"), store.installHistory())
        assertNull(KeyValueWhatsNewStore(storage).lastSeenVersion())
    }

    @Test
    fun corruptHistoryCountsAsEmpty() {
        for (corrupt in listOf("", "not json", "{}", "[1, 2]", """["1.0.0", null]""", """[["1.0.0"]]""", "\"1.0.0\"")) {
            val storage = MapKeyValueStorage(mutableMapOf("cherkit.whatsnew.installHistory" to corrupt))
            assertEquals(emptyList(), KeyValueWhatsNewStore(storage).installHistory(), corrupt)
        }
    }

    @Test
    fun corruptStoreBehavesLikeFirstLaunch() {
        val storage = MapKeyValueStorage(
            mutableMapOf("cherkit.whatsnew.lastSeenVersion" to "garbage", "cherkit.whatsnew.installHistory" to "{"),
        )
        val store = KeyValueWhatsNewStore(storage)
        val checker = WhatsNewChecker(LoadedReleaseNotesSource { notes }, store)
        assertEquals(emptyList(), checker.onLaunch("1.1.0", listOf("en")))
        assertEquals("1.1.0", store.lastSeenVersion())
        assertEquals(listOf("1.1.0"), store.installHistory())
    }

    @Test
    fun throwingLoaderMeansNoNotes() {
        val source = LoadedReleaseNotesSource { error("assets unavailable") }
        assertEquals(emptyMap(), source.notesFiles())
        val checker = WhatsNewChecker(source, KeyValueWhatsNewStore(MapKeyValueStorage()))
        assertEquals(emptyList(), checker.history(listOf("en")))
    }

    @Test
    fun throwingStoreNeverReachesTheApp() {
        val broken = object : WhatsNewStore {
            override fun lastSeenVersion(): String? = error("broken")
            override fun setLastSeenVersion(version: String): Unit = error("broken")
            override fun installHistory(): List<String> = error("broken")
            override fun setInstallHistory(history: List<String>): Unit = error("broken")
        }
        val checker = WhatsNewChecker(LoadedReleaseNotesSource { notes }, broken)
        assertEquals(emptyList(), checker.onLaunch("1.1.0", listOf("en")))
        checker.markSeen()
        assertEquals(emptyList(), checker.history(listOf("en")))
    }
}
