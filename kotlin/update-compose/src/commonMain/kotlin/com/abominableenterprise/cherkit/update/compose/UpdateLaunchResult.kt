package com.abominableenterprise.cherkit.update.compose

/** What one [UpdateLauncher.launch] did, e.g. for logging. Only [STARTED] showed anything. */
public enum class UpdateLaunchResult {
    /** Turned off (debug builds by default). Nothing was asked. */
    DISABLED,

    /** Not installed by the Play Store (sideloaded, an IDE or emulator install, another store). Nothing was asked. */
    NOT_PLAY_INSTALL,

    /** The store request failed (no Play Store, offline, any store error). */
    CHECK_FAILED,

    /** A flexible update is downloaded and waits for `completeUpdate()`: offer "Restart to install". */
    DOWNLOADED,

    /** The flow already started once this session. */
    ALREADY_PROMPTED,

    /** No update, or the flexible flow is not allowed for it. */
    NO_UPDATE,

    /** The user already said "Not now" to this versionCode. */
    DECLINED_BEFORE,

    /** Held back: suppressed (another prompt is up) or the screen left the foreground during the request. */
    SUPPRESSED,

    /** The store refused to start the flow, or threw. */
    START_FAILED,

    /** The store's update sheet is showing; its answer comes as an [UpdateFlowResult]. */
    STARTED,
}
