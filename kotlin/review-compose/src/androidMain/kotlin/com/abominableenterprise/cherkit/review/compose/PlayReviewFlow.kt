package com.abominableenterprise.cherkit.review.compose

import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager

/** [ReviewFlow] over Play In-App Review (review-ktx): `requestReviewFlow()`, then `launchReviewFlow(activity)`. */
internal class PlayReviewFlow(
    private val activity: Activity,
    private val manager: ReviewManager,
) : ReviewFlow<ReviewInfo> {
    override suspend fun request(): ReviewInfo = manager.requestReview()

    override suspend fun launch(info: ReviewInfo) = manager.launchReview(activity, info)
}
