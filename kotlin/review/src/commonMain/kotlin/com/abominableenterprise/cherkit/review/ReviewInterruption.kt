package com.abominableenterprise.cherkit.review

/**
 * Well-known sources for [ReviewCoordinator.setInterruptionVisible]. Apps may use any string of their own
 * (e.g. `"paywall"`); each source is tracked separately, so two modals never clear each other.
 */
public object ReviewInterruption {
    /** Sunset's soft "Update available" dialog (sunset-compose `SunsetPrompt(onVisibilityChange = ...)`). */
    public const val SUNSET_PROMPT: String = "sunset.prompt"

    /** Sunset's hard "Update required" block (sunset-compose `SunsetPrompt(onPromptChange = ...)` reports it). */
    public const val SUNSET_BLOCK: String = "sunset.block"

    /** The "What's new" sheet or screen. The same string as Swift's `ReviewInterruption.whatsNew`. */
    public const val WHATS_NEW: String = "whatsNew"
}
