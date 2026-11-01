package com.abominableenterprise.cherkit.update.compose

/** How the user left the store's flexible update sheet. */
public enum class UpdateFlowResult {
    /** "Update": the download runs in the background; expect DOWNLOADED later. */
    ACCEPTED,

    /** "Not now" (or back): remembered for that versionCode, so it is not offered again. */
    CANCELLED,

    /** The store reported a failure; nothing is remembered. */
    FAILED,
}
