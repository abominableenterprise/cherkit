package com.abominableenterprise.cherkit.sunset.compose

/** Which Sunset prompt is showing, reported by `SunsetPrompt(onPromptChange = ...)`; `null` means none. */
public enum class SunsetPromptKind {
    /** The dismissible "Update available" dialog. */
    SOFT,

    /** The full-screen "Update required" block. */
    HARD,
}
