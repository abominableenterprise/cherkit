package com.abominableenterprise.cherkit.review.compose

/** What one launch attempt did. Only [LAUNCHED] and [LAUNCH_FAILED] consumed this version's prompt. */
public enum class ReviewLaunchResult {
    /** `shouldPrompt` was false (not due, held, not idle, disabled), before or after the request. Nothing changed. */
    SKIPPED,

    /** The build was not installed by the Play Store (`requirePlayInstall`). Nothing changed; the prompt stays due. */
    NOT_PLAY_INSTALL,

    /** The store could not prepare a review. Nothing changed; the prompt stays due. */
    REQUEST_FAILED,

    /** `markPrompted()` recorded the version and the review UI was launched (the store may still show nothing). */
    LAUNCHED,

    /** `markPrompted()` recorded the version, then launching failed. The version counts as prompted. */
    LAUNCH_FAILED,
}
