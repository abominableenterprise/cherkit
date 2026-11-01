package com.abominableenterprise.cherkit.review.compose

import android.app.Activity
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

/** Play In-App Review for a [ReviewCoordinator]. Most apps use `ReviewPromptEffect`, which calls [launch]. */
public object PlayReview {
    /**
     * Shows the Play review UI over [activity] if [coordinator] says it is time. In order:
     *
     * 1. `coordinator.shouldPrompt(isIdle())`, else [ReviewLaunchResult.SKIPPED].
     * 2. With [requirePlayInstall] (the default), [PlayInstall.isPlayInstall]: a build the Play Store did not install
     *    (sideloaded with adb or a file, an emulator install from the IDE, another store) returns
     *    [ReviewLaunchResult.NOT_PLAY_INSTALL] without consuming anything, because Play would show nothing there.
     *    Play testing tracks (internal, closed, open) install through `com.android.vending` and pass.
     * 3. `requestReviewFlow()`. If it fails, [ReviewLaunchResult.REQUEST_FAILED] and the prompt stays due.
     * 4. `markPrompted()`, right before `launchReviewFlow()` ([ReviewLaunchResult.LAUNCHED]).
     *
     * Play gives no signal about whether it showed anything: on a Play-installed build where Play still shows nothing
     * (its quota), the version counts as prompted all the same. Never throws (except cancellation): a Play error
     * returns a result. Call it from the main thread.
     *
     * @param manager The Play API; `FakeReviewManager` in tests.
     */
    public suspend fun launch(
        activity: Activity,
        coordinator: ReviewCoordinator,
        requirePlayInstall: Boolean = true,
        isIdle: () -> Boolean = { true },
        manager: () -> ReviewManager = { ReviewManagerFactory.create(activity) },
    ): ReviewLaunchResult {
        if (!coordinator.shouldPrompt(isIdle())) return ReviewLaunchResult.SKIPPED
        if (requirePlayInstall && !PlayInstall.isPlayInstall(activity)) return ReviewLaunchResult.NOT_PLAY_INSTALL
        val flow = try {
            PlayReviewFlow(activity, manager())
        } catch (_: Exception) {
            return ReviewLaunchResult.REQUEST_FAILED
        }
        return ReviewLauncher.launch(coordinator, flow, isIdle)
    }
}
