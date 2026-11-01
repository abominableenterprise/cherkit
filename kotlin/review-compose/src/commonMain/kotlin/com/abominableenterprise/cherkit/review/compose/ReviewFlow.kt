package com.abominableenterprise.cherkit.review.compose

/**
 * A platform review API in two steps, so [ReviewLauncher] can call `markPrompted()` between them and tests can fake
 * it. On Android, `PlayReview` wraps Play In-App Review's `requestReviewFlow()` and `launchReviewFlow()`.
 *
 * @param T What [request] hands to [launch] (Play's `ReviewInfo`).
 */
public interface ReviewFlow<T : Any> {
    /** Prepares the review UI. Throws when the store can't show one (no Play Store, no network, an API error). */
    public suspend fun request(): T

    /** Shows the review UI prepared by [request]. May show nothing, and does not say whether it did. */
    public suspend fun launch(info: T)
}
