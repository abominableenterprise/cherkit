package com.abominableenterprise.cherkit.whatsnew.compose

import com.abominableenterprise.cherkit.whatsnew.KeyValueStorage
import com.abominableenterprise.cherkit.whatsnew.KeyValueWhatsNewStore
import com.abominableenterprise.cherkit.whatsnew.LoadedReleaseNotesSource
import com.abominableenterprise.cherkit.whatsnew.WhatsNewChecker

/**
 * A real [WhatsNewChecker] over in-memory notes and storage, after an update from 1.0.0 to 1.1.0 (and 1.2.0 with
 * [twoReleases]): `onLaunch("1.1.0")` returns notes, and [lastSeen] only becomes the new version on `markSeen()`.
 */
internal class TestChecker(twoReleases: Boolean = false) {
    private val values = mutableMapOf(
        "cherkit.whatsnew.lastSeenVersion" to "1.0.0",
        "cherkit.whatsnew.installHistory" to """["1.0.0"]""",
    )

    /** How often `onLaunch` read the notes, i.e. how often it ran. */
    var launches = 0
        private set

    private val files = buildList {
        add("1.0.0.json" to """{"version":"1.0.0","notes":{"en":["First release."]}}""")
        add("1.1.0.json" to """{"version":"1.1.0","date":"2026-11-02","notes":{"en":["CSV export.","Dark mode."]}}""")
        if (twoReleases) add("1.2.0.json" to """{"version":"1.2.0","notes":{"en":["Widgets."]}}""")
    }

    val checker = WhatsNewChecker(
        LoadedReleaseNotesSource {
            launches++
            files
        },
        KeyValueWhatsNewStore(
            object : KeyValueStorage {
                override fun getString(key: String): String? = values[key]
                override fun putString(key: String, value: String) {
                    values[key] = value
                }
            },
        ),
    )

    /** The stored last-seen version: still `1.0.0` until `markSeen()` runs. */
    val lastSeen: String? get() = values["cherkit.whatsnew.lastSeenVersion"]
}
