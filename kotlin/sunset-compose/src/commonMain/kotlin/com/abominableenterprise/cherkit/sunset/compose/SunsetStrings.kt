package com.abominableenterprise.cherkit.sunset.compose

import com.abominableenterprise.cherkit.sunset.SunsetPrompt

/**
 * The text of the Sunset prompts. English defaults, the same as iOS's `SunsetStrings`; pass your own (already
 * localized) text to override any of them. (iOS's VoiceOver `updateHint` has no counterpart: TalkBack has no hints.)
 */
public data class SunsetStrings(
    /** Title of the soft, dismissible prompt. */
    val softTitle: String = "Update available",
    /** Soft prompt text when the config has no custom message. */
    val softMessage: String = "A new version of the app is ready, with the latest improvements and fixes.",
    /** Title of the hard block. */
    val hardTitle: String = "Update required",
    /** Hard block text when the config has no custom message. */
    val hardMessage: String = "This version is no longer supported. Please update to keep using the app.",
    /** The button that opens the store page. */
    val updateButton: String = "Update",
    /** The soft prompt's dismiss button. Tapping it hides the prompt for that version. */
    val notNowButton: String = "Not now",
) {
    /** The text for the soft prompt: the config's custom message, or [softMessage] when it has none. */
    public fun softText(prompt: SunsetPrompt): String = custom(prompt.message) ?: softMessage

    /** The text for the hard block: the config's custom message, or [hardMessage] when it has none. */
    public fun hardText(prompt: SunsetPrompt): String = custom(prompt.message) ?: hardMessage

    /** [message], or `null` when it is missing or blank (so the screen never shows an empty line). */
    private fun custom(message: String?): String? = message?.takeUnless { it.isBlank() }
}
