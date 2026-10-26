package com.abominableenterprise.cherkit.sunset.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abominableenterprise.cherkit.sunset.AndroidSunset
import com.abominableenterprise.cherkit.sunset.SunsetCheckOutcome
import com.abominableenterprise.cherkit.sunset.SunsetCheckScheduler
import com.abominableenterprise.cherkit.sunset.SunsetChecker
import kotlin.time.Duration

/**
 * Checks for an update with [checker] and shows the Sunset prompt, like iOS's `.sunsetPrompt(checker:)`. Put it once
 * in your root composable, inside your `MaterialTheme`:
 *
 * ```kotlin
 * SunsetPrompt(checker = if (isUiTest) SunsetChecker.disabled() else checker)
 * ```
 *
 * When it checks (with the same app version, OS version and locales as `AndroidSunset.check(checker, context)`):
 *
 * - Once at launch, on the first composition. Only once per process, even if it is recomposed, the root is rebuilt
 *   or the activity is recreated (a rotation is not a return from the background either).
 * - Again when the app comes back from the background (Lifecycle `ON_STOP`, then `ON_RESUME`), at most once per
 *   [minimumInterval]. The first `ON_RESUME` and `ON_PAUSE` → `ON_RESUME` (a system dialog, a permission prompt) do
 *   not check, so the launch fetch never runs twice.
 * - Never while a check is still running.
 * - After a failed check (the fetch failed, timed out or did not parse, and no usable config is cached, e.g. a fresh
 *   install with a dropped connection), once more about 3 seconds later. A failed check does not count toward
 *   [minimumInterval]: if the retry fails too, the next return from the background re-checks whatever the interval.
 *
 * What each decision shows is described on the decision-based [SunsetPrompt]. A re-check replaces the decision: a
 * hard block goes away once the config no longer requires the update, and a soft dialog answered with "Not now"
 * stays hidden for that `latestVersion`. [SunsetChecker.disabled] never shows anything.
 *
 * @param checker The checker, e.g. `AndroidSunset.checker(context) { ... }`, or [SunsetChecker.disabled] in UI tests.
 * @param minimumInterval The least time between the start of one check and a re-check on return from the
 *   background. One hour by default; [Duration.ZERO] re-checks on every return.
 * @param memory Where "Not now" is stored.
 * @param strings The text; English defaults.
 * @param appIcon Optional icon above the "Update required" title. Decorative: TalkBack skips it.
 * @param onVisibilityChange Called with `true` when the soft "Update available" dialog appears and `false` when it
 *   goes away, e.g. to hold back your own work while it is open. Not called for the hard block.
 */
@Composable
public fun SunsetPrompt(
    checker: SunsetChecker,
    minimumInterval: Duration = SunsetCheckScheduler.DEFAULT_MINIMUM_INTERVAL,
    memory: SunsetSoftPromptMemory = rememberSunsetSoftPromptMemory(),
    strings: SunsetStrings = SunsetStrings(),
    appIcon: Painter? = null,
    onVisibilityChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    SunsetCheckerPrompt(
        runner = SunsetCheckRunner.shared,
        minimumInterval = minimumInterval,
        memory = memory,
        strings = strings,
        appIcon = appIcon,
        onVisibilityChange = onVisibilityChange,
        check = {
            checker.checkReportingFailure(
                AndroidSunset.installedVersion(context),
                AndroidSunset.osVersion(),
                AndroidSunset.preferredLocales(),
            )
        },
    )
}

/** [SunsetPrompt] (checker) with its [runner] and [check], so host tests can pass their own. */
@Composable
internal fun SunsetCheckerPrompt(
    runner: SunsetCheckRunner,
    minimumInterval: Duration,
    memory: SunsetSoftPromptMemory,
    strings: SunsetStrings,
    appIcon: Painter?,
    onVisibilityChange: (Boolean) -> Unit,
    check: suspend () -> SunsetCheckOutcome,
) {
    val currentCheck by rememberUpdatedState(check)
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()
    SideEffect { runner.setMinimumInterval(minimumInterval) }
    DisposableEffect(runner, lifecycleOwner, activity) {
        runner.setMinimumInterval(minimumInterval)
        runner.launched { currentCheck() }
        // Adding the observer replays the events up to the current state (ON_CREATE ... ON_RESUME): no ON_STOP, so
        // no check.
        val observer = LifecycleEventObserver { _, event ->
            // A rotation or other configuration change stops the old activity, but the app never left the screen.
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations == true) return@LifecycleEventObserver
            runner.lifecycleEvent(event) { currentCheck() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SunsetPrompt(
        decision = runner.decision,
        onDecisionHandled = { runner.decision = null },
        memory = memory,
        strings = strings,
        appIcon = appIcon,
        onVisibilityChange = onVisibilityChange,
    )
}

/** The activity this context belongs to, if any. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}
