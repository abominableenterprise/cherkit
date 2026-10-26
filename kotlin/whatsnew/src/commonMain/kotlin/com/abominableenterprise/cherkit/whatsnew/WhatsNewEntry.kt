package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion

/**
 * One release to show in the "What's new" sheet, from [WhatsNewChecker.onLaunch].
 *
 * @property version The release.
 * @property date Release date as written in the notes file, or `null`. Shown as-is.
 * @property bullets The bullets in the user's locale (see [LocaleFallback]).
 */
public data class WhatsNewEntry(public val version: AppVersion, public val date: String?, public val bullets: List<String>)

/**
 * One bundled release in the full history list, from [WhatsNewChecker.history].
 *
 * @property version The release.
 * @property date Release date as written in the notes file, or `null`. Shown as-is.
 * @property bullets The bullets in the user's locale; empty for a fix-only release without notes.
 * @property isInstalled `true` for the version installed now ("you are here").
 */
public data class WhatsNewHistoryEntry(
    public val version: AppVersion,
    public val date: String?,
    public val bullets: List<String>,
    public val isInstalled: Boolean,
)
