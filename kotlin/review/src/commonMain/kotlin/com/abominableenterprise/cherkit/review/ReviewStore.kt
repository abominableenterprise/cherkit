package com.abominableenterprise.cherkit.review

/**
 * Where [ReviewCoordinator] keeps its [ReviewState]. Built in: [KeyValueReviewStore] (on Android over
 * `SharedPreferencesReviewStorage`, see `AndroidReview.store`) and [InMemoryReviewStore] (tests).
 * Calls happen on the caller's thread and must be fast (local storage).
 */
public interface ReviewStore {
    /** The stored state; `ReviewState()` when nothing (readable) is stored. Must not throw. */
    public fun load(): ReviewState

    /** Replaces the stored state. */
    public fun save(state: ReviewState)
}
