package com.abominableenterprise.cherkit.update.compose

/** What the store reported about an update, in plain terms (Play's `AppUpdateInfo` on Android). */
public data class UpdateStatus(
    /** The store offers a newer build (Play: `updateAvailability == UPDATE_AVAILABLE`). */
    val updateAvailable: Boolean,
    /** The flexible (background download) flow is allowed for it (Play: `isUpdateTypeAllowed(FLEXIBLE)`). */
    val flexibleAllowed: Boolean,
    /** The offered build's versionCode; "Not now" is remembered per value. */
    val availableVersionCode: Int,
    /** A flexible update has finished downloading and waits for `completeUpdate()` (Play: `DOWNLOADED`). */
    val downloaded: Boolean,
)
