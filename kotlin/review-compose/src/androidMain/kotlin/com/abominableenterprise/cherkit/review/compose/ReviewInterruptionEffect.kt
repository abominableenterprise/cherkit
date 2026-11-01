package com.abominableenterprise.cherkit.review.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.abominableenterprise.cherkit.review.ReviewCoordinator

/**
 * Reports one of your dialogs, sheets or banners to [coordinator] as an interruption: while [visible] the review
 * never shows; when it turns false (or this leaves the composition) the coordinator's cooldown starts.
 *
 * ```kotlin
 * ReviewInterruptionEffect(review, source = "paywall", visible = showPaywall)
 * ```
 *
 * Safe for sources that flicker (an error banner toggling quickly): every hide restarts the cooldown, so the review
 * waits a full cooldown after the last one. Give each source its own [source] string: two effects with the same
 * string share one entry, and the first to hide clears it.
 */
@Composable
public fun ReviewInterruptionEffect(coordinator: ReviewCoordinator, source: String, visible: Boolean) {
    DisposableEffect(coordinator, source, visible) {
        if (visible) coordinator.setInterruptionVisible(true, source)
        onDispose { if (visible) coordinator.setInterruptionVisible(false, source) }
    }
}
