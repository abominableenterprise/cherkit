package com.abominableenterprise.cherkit.review.compose

import com.abominableenterprise.cherkit.review.ReviewCoordinator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The settle-and-check loop behind `ReviewPromptEffect`; platform-free so it is tested with virtual time. */
public object ReviewSettle {
    /** How long the screen must stay idle before the review may show. */
    public val DEFAULT_DELAY: Duration = 1500.milliseconds

    /**
     * Waits until the review may show on a screen that stays idle, then calls [launch] and returns its first result
     * other than [ReviewLaunchResult.SKIPPED]. Call it only while the screen is idle and in front; cancel it when
     * either stops being true.
     *
     * - Waits [delay] first, so the review never shows the moment a screen appears.
     * - Then checks `shouldPrompt()`; while it is false, checks again every [delay]. While an interruption's
     *   cooldown runs it waits the cooldown out, then [delay] again, so a cleared modal never fires the review at
     *   once.
     * - A [ReviewLaunchResult.SKIPPED] launch (e.g. a modal opened while the store prepared the review) keeps waiting
     *   the same way. Any other result ends the wait: a failed request is not retried until the next call.
     * - A disabled coordinator returns [ReviewLaunchResult.SKIPPED] at once and never waits.
     */
    public suspend fun awaitThenLaunch(
        coordinator: ReviewCoordinator,
        delay: Duration = DEFAULT_DELAY,
        launch: suspend () -> ReviewLaunchResult,
    ): ReviewLaunchResult {
        if (coordinator.isDisabled) return ReviewLaunchResult.SKIPPED
        val step = delay.coerceAtLeast(MIN_DELAY)
        while (true) {
            kotlinx.coroutines.delay(step)
            if (coordinator.shouldPrompt()) {
                val result = launch()
                if (result != ReviewLaunchResult.SKIPPED) return result
            }
            val cooldown = coordinator.cooldownRemaining
            if (cooldown > Duration.ZERO) kotlinx.coroutines.delay(cooldown)
        }
    }

    /** Keeps a zero delay from spinning. */
    private val MIN_DELAY = 100.milliseconds
}
