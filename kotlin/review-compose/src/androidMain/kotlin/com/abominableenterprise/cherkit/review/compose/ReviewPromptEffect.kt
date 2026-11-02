package com.abominableenterprise.cherkit.review.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.abominableenterprise.cherkit.core.TelemetrySink
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import kotlin.time.Duration

/**
 * Asks for a Play review when [coordinator] says it is due, once the screen has settled. Put it on your main
 * screens (not over a modal), and report your own modals with [ReviewInterruptionEffect]:
 *
 * ```kotlin
 * ReviewPromptEffect(review, isIdle = !isEditing)
 * ```
 *
 * - Runs only while the lifecycle is `RESUMED` and [isIdle] is true. It waits [settleDelay] first, then checks
 *   `shouldPrompt()`, and checks again every [settleDelay] (after any interruption cooldown) while the screen stays
 *   idle, so a prompt that becomes due on this screen still shows.
 * - Starts over on every lifecycle `ON_RESUME` and every time [isIdle] turns true: leaving the app, a system
 *   dialog or a busy screen cancels the wait.
 * - Launches through [PlayReview.launch] with the Activity from `LocalContext` (nothing without one, e.g. in a
 *   preview). A run ends at its first attempt that gets past `shouldPrompt`: launched, not a Play install
 *   ([requirePlayInstall], see there) or a failed Play request, which leaves the prompt due for the next run.
 *   An attempt skipped because a modal opened during the Play request keeps the run waiting.
 * - The version counts as prompted once Play's request succeeded, even if Play then shows nothing (its quota):
 *   Play gives no signal either way.
 * - A disabled coordinator (debug builds by default) does nothing.
 *
 * @param onResult Called with the result that ended each run, e.g. for logging.
 * @param telemetrySink Optional Play failure diagnostics. Pass it to the coordinator too for prompt breadcrumbs.
 */
@Composable
public fun ReviewPromptEffect(
    coordinator: ReviewCoordinator,
    isIdle: Boolean = true,
    settleDelay: Duration = ReviewSettle.DEFAULT_DELAY,
    requirePlayInstall: Boolean = true,
    telemetrySink: TelemetrySink? = null,
    onResult: (ReviewLaunchResult) -> Unit = {},
) {
    ReviewPromptEffect(coordinator, isIdle, settleDelay, onResult) { activity, idle ->
        PlayReview.launch(activity, coordinator, requirePlayInstall, idle, telemetrySink = telemetrySink)
    }
}

/** Keeps positional result callbacks source-compatible without changing named or trailing-lambda calls. */
@Suppress("UNUSED_PARAMETER")
@Composable
public fun ReviewPromptEffect(
    coordinator: ReviewCoordinator,
    isIdle: Boolean = true,
    settleDelay: Duration = ReviewSettle.DEFAULT_DELAY,
    requirePlayInstall: Boolean = true,
    legacyOnResult: (ReviewLaunchResult) -> Unit,
    compatibility: Unit = Unit,
) {
    ReviewPromptEffect(coordinator, isIdle, settleDelay, requirePlayInstall, telemetrySink = null, onResult = legacyOnResult)
}

/** The original composable signature, retained for already compiled callers. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
public fun ReviewPromptEffect(
    coordinator: ReviewCoordinator,
    isIdle: Boolean = true,
    settleDelay: Duration = ReviewSettle.DEFAULT_DELAY,
    requirePlayInstall: Boolean = true,
    onResult: (ReviewLaunchResult) -> Unit = {},
) {
    ReviewPromptEffect(coordinator, isIdle, settleDelay, requirePlayInstall, telemetrySink = null, onResult = onResult)
}

/** [ReviewPromptEffect] with its [launch], so host tests can fake Play. */
@Composable
internal fun ReviewPromptEffect(
    coordinator: ReviewCoordinator,
    isIdle: Boolean,
    settleDelay: Duration,
    onResult: (ReviewLaunchResult) -> Unit,
    launch: suspend (Activity, isIdle: () -> Boolean) -> ReviewLaunchResult,
) {
    val activity = LocalContext.current.findActivity()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val currentIdle by rememberUpdatedState(isIdle)
    val currentResumed by rememberUpdatedState(resumed)
    val currentLaunch by rememberUpdatedState(launch)
    val currentOnResult by rememberUpdatedState(onResult)
    // Keyed on `resumed`: every ON_RESUME (after an ON_PAUSE) starts a fresh settle and check.
    LaunchedEffect(coordinator, activity, isIdle, resumed, settleDelay) {
        if (activity == null || !isIdle || !resumed) return@LaunchedEffect
        val result = ReviewSettle.awaitThenLaunch(coordinator, settleDelay) {
            currentLaunch(activity) { currentIdle && currentResumed }
        }
        currentOnResult(result)
    }
}

/** The activity this context belongs to, if any. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}
