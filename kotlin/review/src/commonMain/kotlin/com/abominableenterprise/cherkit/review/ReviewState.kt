package com.abominableenterprise.cherkit.review

/**
 * The review prompt's persisted, per-device state. [ReviewCoordinator] reads it once at construction and writes it
 * through its [ReviewStore] on every change.
 */
public data class ReviewState(
    /** Successes recorded since the last prompt (all versions). Capped at `Int.MAX_VALUE`; negative counts as 0. */
    val successCount: Int = 0,
    /** A trigger fired and no prompt has been shown for it yet. */
    val isDue: Boolean = false,
    /** The app version that last showed the prompt, or `null` before the first one. */
    val lastPromptedVersion: String? = null,
)
