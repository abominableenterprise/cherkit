package com.abominableenterprise.cherkit.sunset

/** What the app should show for the installed version. */
public sealed interface SunsetDecision {
    /** The prompt to show, or `null` for [UpToDate]. */
    public val prompt: SunsetPrompt?

    /** No prompt. Also the fail-open answer for a missing, malformed or unusable config. */
    public data object UpToDate : SunsetDecision {
        override val prompt: SunsetPrompt? get() = null
    }

    /** A newer version exists: show a dismissible "update available" prompt. */
    public data class SoftUpdate(override val prompt: SunsetPrompt) : SunsetDecision

    /** The installed version is no longer supported: show a blocking "update required" screen. */
    public data class HardUpdate(override val prompt: SunsetPrompt) : SunsetDecision
}
