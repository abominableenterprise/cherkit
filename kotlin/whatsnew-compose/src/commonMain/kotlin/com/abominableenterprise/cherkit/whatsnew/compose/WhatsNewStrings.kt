package com.abominableenterprise.cherkit.whatsnew.compose

import com.abominableenterprise.cherkit.whatsnew.StoreText

/**
 * The text of the "What's new" sheet and the version history. English defaults, the same as iOS's `WhatsNewStrings`;
 * pass your own (already localized) text to override any of them, e.g. `WhatsNewStrings(title = "Neuigkeiten")`.
 */
public data class WhatsNewStrings(
    /** Sheet title. Default: "What's New". */
    val title: String = "What's New",
    /** The sheet's dismiss button. Default: "Continue". */
    val continueButton: String = "Continue",
    /** Heading for one release, from its version (e.g. `1.4.0`). Default: "Version 1.4.0". */
    val versionTitle: (String) -> String = DEFAULT_VERSION_TITLE,
    /**
     * Title for the history screen. [WhatsNewHistoryScreen] does not show it: put it in your own top bar.
     * Default: "Version History".
     */
    val historyTitle: String = "Version History",
    /** Badge on the installed release in the history. Default: "You're on this version". */
    val installedBadge: String = "You're on this version",
    /** Shown for a history release without bullets (fix-only). Default: "Bug fixes and improvements.". */
    val noNotes: String = StoreText.FIX_ONLY_TEXT,
    /** Shown when the history has no releases. Default: "No release notes yet.". */
    val emptyHistory: String = "No release notes yet.",
)

/** One shared instance, so `WhatsNewStrings() == WhatsNewStrings()`. */
private val DEFAULT_VERSION_TITLE: (String) -> String = { "Version $it" }
