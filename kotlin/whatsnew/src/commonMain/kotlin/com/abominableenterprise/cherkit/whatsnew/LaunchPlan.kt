package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion

/**
 * What to do with "What's new" on app launch. Build it with [LaunchPlan.make].
 *
 * @property show Notes to show now, newest first; empty means show nothing.
 * @property lastSeen The last-seen version to store after showing [show] (normalized, e.g. `1.4.0`), or `null` to
 *   store nothing.
 */
public data class LaunchPlan(public val show: List<ReleaseNotes>, public val lastSeen: String?) {
    public companion object {
        /**
         * The plan for [installed] (the running app's version string) given every bundled [notes] and the stored
         * [lastSeen] version (`null` on first launch). Contract: `fixtures/whatsnew/launch.json`.
         *
         * - First launch (or an unreadable [lastSeen]): show nothing, store [installed].
         * - Update: show the `show == true` notes with `lastSeen < version <= installed`, newest first; store
         *   [installed].
         * - Same version or downgrade: show nothing, keep [lastSeen], so re-upgrading does not repeat notes.
         * - Unreadable [installed]: fail open, show nothing and keep [lastSeen].
         *
         * When two notes have the same version (`1.3`, `1.3.0`) the first one wins.
         */
        public fun make(notes: List<ReleaseNotes>, installed: String, lastSeen: String?): LaunchPlan {
            val current = AppVersion.parse(installed) ?: return LaunchPlan(emptyList(), lastSeen)
            val seen = lastSeen?.let(AppVersion::parse) ?: return LaunchPlan(emptyList(), current.toString())
            if (seen >= current) return LaunchPlan(emptyList(), lastSeen)
            val shown = notes
                .distinctBy { it.version }
                .filter { it.show && seen < it.version && it.version <= current }
                .sortedByDescending { it.version }
            return LaunchPlan(shown, current.toString())
        }
    }
}
