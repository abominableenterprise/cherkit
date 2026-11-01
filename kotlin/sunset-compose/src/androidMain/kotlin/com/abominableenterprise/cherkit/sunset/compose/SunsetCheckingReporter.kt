package com.abominableenterprise.cherkit.sunset.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Reports [runner]'s checks to `onCheckingChange`: `true` when one starts (launch, re-check or retry), `false` once it
 * has finished and the prompt has shown its result. Main thread only.
 *
 * - The `true` comes from the runner's [SunsetCheckRunner.checkStartedListeners], at once when the check starts (in
 *   the Lifecycle callback for a re-check), so state it sets is written before the next frame. [Attach] also reports a
 *   check already running when this is composed (e.g. after a rotation).
 * - The `false` comes from [End], composed after the prompt: effects run in composition order, so the prompt's
 *   `onPromptChange` / `onVisibilityChange` for the result run first. The runner counts checks started and finished
 *   rather than holding a flag, so a check that starts and finishes between two frames still gets its `false`.
 *
 * Each `false` goes to the callback that got the matching `true`; a new lambda instance alone sends nothing.
 */
internal class SunsetCheckingReporter(
    private val runner: SunsetCheckRunner,
    private val onCheckingChange: State<((Boolean) -> Unit)?>,
    private val enabled: State<Boolean>,
) {
    /** The checks started that are accounted for: reported, or started while there was nothing to report to. */
    private var seen = runner.checksStarted

    /** The callback that got `true` and waits for its `false`, or `null` when no check is reported as running. */
    private var recipient: ((Boolean) -> Unit)? = null

    /** The started count the last `true` covered: the `false` waits until the prompt has composed that many results. */
    private var reportedUpTo = 0

    /** A check started, or one may be running that is not reported yet: report `true` for it. */
    private fun catchUp() {
        // Already reporting one: End reports the newer check after the `false` for this one.
        if (recipient != null) return
        val live = runner.checksStarted
        if (enabled.value && (live > seen || live > runner.checksFinished)) reportTrue(live) else seen = live
    }

    /** Before the effect that starts the launch check: listens for checks and reports one already running. */
    @Composable
    fun Attach() {
        val hasCallback = onCheckingChange.value != null
        DisposableEffect(this) {
            val listener = ::catchUp
            runner.checkStartedListeners += listener
            onDispose {
                runner.checkStartedListeners -= listener
                // Composed before the prompt, so on leaving the composition this runs after the prompt's reports.
                reportFalse()
            }
        }
        // A callback (or the checker) that arrives while a check runs learns about it.
        DisposableEffect(this, hasCallback, enabled.value) {
            catchUp()
            onDispose {}
        }
    }

    /**
     * After the prompt: `false` once the prompt has been composed with every reported check's result ([finished] is
     * the count that composition read). A check started after the reported one then gets its own `true`, and its
     * `false` too if this composition already shows its result.
     */
    @Composable
    fun End(finished: Int) {
        val hasCallback = onCheckingChange.value != null
        DisposableEffect(this, finished, enabled.value, hasCallback) {
            if (recipient != null && (finished >= reportedUpTo || !enabled.value || !hasCallback)) {
                reportFalse()
                catchUp()
                if (recipient != null && finished >= reportedUpTo) reportFalse()
            }
            onDispose {}
        }
    }

    private fun reportTrue(started: Int) {
        seen = started
        val callback = onCheckingChange.value ?: return
        recipient = callback
        reportedUpTo = started
        callback(true)
    }

    private fun reportFalse() {
        val callback = recipient ?: return
        recipient = null
        callback(false)
    }
}

/** A [SunsetCheckingReporter] for this composition, reading [onCheckingChange] through `rememberUpdatedState`. */
@Composable
internal fun rememberSunsetCheckingReporter(
    runner: SunsetCheckRunner,
    onCheckingChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
): SunsetCheckingReporter {
    val currentOnCheckingChange = rememberUpdatedState(onCheckingChange)
    val currentEnabled = rememberUpdatedState(enabled)
    return remember(runner) { SunsetCheckingReporter(runner, currentOnCheckingChange, currentEnabled) }
}
