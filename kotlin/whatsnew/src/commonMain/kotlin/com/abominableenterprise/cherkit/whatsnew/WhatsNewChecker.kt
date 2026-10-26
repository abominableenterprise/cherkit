package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion
import kotlin.jvm.Synchronized

/**
 * The one call apps make at launch: which "What's new" notes to show, plus the full history list.
 * Contract: `fixtures/whatsnew/checker.json`. Never throws; anything unreadable fails open (shows nothing).
 * Calls are synchronized, so any thread may use it. On Android, `AndroidWhatsNew.checker(context)`
 * wires the assets source and SharedPreferences store.
 */
public class WhatsNewChecker(private val source: ReleaseNotesSource, private val store: WhatsNewStore) {
    private var pendingLastSeen: String? = null

    /**
     * Call once per launch. Appends [installedVersion] to the install history and returns the notes to show, newest
     * first, with bullets in the user's [locale] (most preferred first). An empty result means show nothing; the
     * version is then marked seen at once. Otherwise call [markSeen] once the notes were shown, so a crash before
     * display shows them again next launch. First launch and downgrades show nothing (see [LaunchPlan]).
     */
    @Synchronized
    public fun onLaunch(installedVersion: String, locale: List<String>): List<WhatsNewEntry> = failOpen(emptyList()) {
        val history = store.installHistory()
        val updated = InstallHistory.updated(history, installedVersion)
        if (updated != history) store.setInstallHistory(updated)

        val lastSeen = store.lastSeenVersion()
        val plan = LaunchPlan.make(source.releaseNotes(), installedVersion, lastSeen)
        if (plan.show.isEmpty()) {
            pendingLastSeen = null
            plan.lastSeen?.takeIf { it != lastSeen }?.let(store::setLastSeenVersion)
            return@failOpen emptyList()
        }
        pendingLastSeen = plan.lastSeen
        plan.show.map { WhatsNewEntry(it.version, it.date, it.bullets(locale)) }
    }

    /**
     * Marks the notes returned by the last [onLaunch] as seen. Call it after showing them; calling it again, or when
     * nothing was shown, does nothing.
     */
    @Synchronized
    public fun markSeen() {
        failOpen(Unit) { pendingLastSeen?.let(store::setLastSeenVersion) }
        pendingLastSeen = null
    }

    /**
     * Every bundled release newest first, with bullets in the user's [locale], for the full history list.
     * [WhatsNewHistoryEntry.isInstalled] marks the latest install-history entry ("you are here").
     */
    public fun history(locale: List<String>): List<WhatsNewHistoryEntry> = failOpen(emptyList()) {
        val installed = store.installHistory().lastOrNull()?.let(AppVersion::parse)
        source.releaseNotes()
            .distinctBy { it.version }
            .sortedByDescending { it.version }
            .map { WhatsNewHistoryEntry(it.version, it.date, it.bullets(locale), it.version == installed) }
    }

    /** A throwing app-supplied store or source must not crash the app at launch. */
    private inline fun <T> failOpen(fallback: T, body: () -> T): T = try {
        body()
    } catch (_: Exception) {
        fallback
    }
}
