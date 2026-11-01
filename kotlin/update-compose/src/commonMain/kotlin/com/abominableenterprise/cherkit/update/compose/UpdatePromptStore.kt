package com.abominableenterprise.cherkit.update.compose

/** Where "Not now" is remembered: the versionCode the user last declined. */
public interface UpdatePromptStore {
    /** The declined versionCode, or `null` when never. */
    public val declinedVersionCode: Int?

    /** Records "Not now" for [versionCode]. Any other versionCode is offered again. */
    public fun rememberDeclined(versionCode: Int)
}
