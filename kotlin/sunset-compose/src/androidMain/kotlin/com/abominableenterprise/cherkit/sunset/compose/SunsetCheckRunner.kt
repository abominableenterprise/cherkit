package com.abominableenterprise.cherkit.sunset.compose

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.abominableenterprise.cherkit.sunset.SunsetCheckOutcome
import com.abominableenterprise.cherkit.sunset.SunsetCheckScheduler
import com.abominableenterprise.cherkit.sunset.SunsetDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the checks for `SunsetPrompt(checker = ...)` when [SunsetCheckScheduler] says so, and holds the latest
 * decision for the prompts. The Android twin of CherKitUI's `SunsetCheckRunner`.
 *
 * The composable uses one [shared] runner for the process, so the launch check runs once per process and the
 * decision (a hard block included) survives recomposition, the root being rebuilt and activity recreation.
 * Call it from the main thread only; checks run in [scope] (the main dispatcher for [shared]).
 *
 * A check that finds no usable config ([SunsetCheckOutcome.failed]: the fetch failed and nothing usable is cached) is
 * retried once after [retryDelay]; if that fails too, the scheduler re-checks on the next return from the background.
 */
internal class SunsetCheckRunner(
    minimumInterval: Duration = SunsetCheckScheduler.DEFAULT_MINIMUM_INTERVAL,
    /** How long to wait before the one quick retry after a failed check. */
    val retryDelay: Duration = DEFAULT_RETRY_DELAY,
    private val now: () -> Duration = { SystemClock.elapsedRealtime().milliseconds },
    private val scope: CoroutineScope = MainScope(),
) {
    /** The latest decision, `null` before the first check finishes. Answering the soft dialog sets it back to `null`. */
    var decision: SunsetDecision? by mutableStateOf(null)

    /** When to check. */
    val scheduler: SunsetCheckScheduler = SunsetCheckScheduler(minimumInterval)

    /** The last check started, running or finished; `null` before the first. */
    var job: Job? = null
        private set

    /** The quick retry waiting for [retryDelay] after a failed check, if any. */
    var retryJob: Job? = null
        private set

    /** Sets the least time between re-checks (the composable's `minimumInterval`). */
    fun setMinimumInterval(interval: Duration) {
        scheduler.minimumInterval = interval
    }

    /** The first composition: checks once per runner. */
    fun launched(check: suspend () -> SunsetCheckOutcome) {
        if (scheduler.shouldCheckAtLaunch(now())) run(check)
    }

    /** A Lifecycle event: checks on a return from the background (`ON_STOP`, then `ON_RESUME`). */
    fun lifecycleEvent(event: Lifecycle.Event, check: suspend () -> SunsetCheckOutcome) {
        if (scheduler.shouldCheck(phase(event), now())) run(check)
    }

    private fun run(check: suspend () -> SunsetCheckOutcome) {
        retryJob?.cancel()
        retryJob = null
        var failed = false
        // Started lazily so `job` is set before the check runs (and before any retry it schedules).
        val started = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = check()
                decision = outcome.decision
                failed = outcome.failed
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The checker never throws; should a check still throw, keep the last decision (fail open).
            } finally {
                // Even after an exception or cancellation, so a later return from the background can check again.
                if (scheduler.checkFinished(failed)) scheduleRetry(check)
            }
        }
        job = started
        started.start()
    }

    /** The one quick retry after a failed check, unless another check started meanwhile. */
    private fun scheduleRetry(check: suspend () -> SunsetCheckOutcome) {
        val retry = scope.launch(start = CoroutineStart.LAZY) {
            delay(retryDelay)
            retryJob = null
            if (scheduler.shouldRetry(now())) run(check)
        }
        retryJob = retry
        retry.start()
    }

    companion object {
        /** The default [retryDelay]: 3 seconds. */
        val DEFAULT_RETRY_DELAY: Duration = 3.seconds

        /** The runner every `SunsetPrompt(checker = ...)` uses. */
        val shared: SunsetCheckRunner by lazy { SunsetCheckRunner() }

        /** `ON_STOP` is the background, `ON_RESUME` active; everything else (`ON_PAUSE`, `ON_START`, ...) inactive. */
        fun phase(event: Lifecycle.Event): SunsetCheckScheduler.Phase = when (event) {
            Lifecycle.Event.ON_STOP -> SunsetCheckScheduler.Phase.BACKGROUND
            Lifecycle.Event.ON_RESUME -> SunsetCheckScheduler.Phase.ACTIVE
            else -> SunsetCheckScheduler.Phase.INACTIVE
        }
    }
}
