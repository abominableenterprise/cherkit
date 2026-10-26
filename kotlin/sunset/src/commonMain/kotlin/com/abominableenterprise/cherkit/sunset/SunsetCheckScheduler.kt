package com.abominableenterprise.cherkit.sunset

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Decides when an app re-runs [SunsetChecker.check]: the same rules as CherKitUI's `SunsetCheckScheduler` behind
 * iOS's `.sunsetPrompt(checker:)`, and what sunset-compose's `SunsetPrompt(checker = ...)` uses. Pure (the caller
 * passes the time), so its rules are unit-tested on their own:
 *
 * - Launch: the first [shouldCheckAtLaunch] checks; later calls never do.
 * - Return from background: [Phase.ACTIVE] checks only after a [Phase.BACKGROUND] since the last [Phase.ACTIVE].
 *   The initial [Phase.ACTIVE] and [Phase.INACTIVE] → [Phase.ACTIVE] (a system dialog, the recents screen) do not.
 *   [Phase.BACKGROUND] → [Phase.INACTIVE] → [Phase.ACTIVE] does.
 * - Never two at once: nothing starts while a check is in flight (until [checkFinished]).
 * - Re-checks wait at least [minimumInterval] since the last successful check started. The launch check always
 *   runs.
 * - A failed check (no usable config: the fetch failed and nothing usable is cached, see
 *   [SunsetChecker.checkReportingFailure]) does not count toward [minimumInterval]. The first failure in a row asks for
 *   one quick retry ([shouldRetry]); while the last check failed, the next return from the background re-checks
 *   whatever the interval.
 *
 * On Android, map Lifecycle events to phases: `ON_STOP` → [Phase.BACKGROUND], `ON_RESUME` → [Phase.ACTIVE], any
 * other event → [Phase.INACTIVE]. Not thread-safe: call it from one thread (the main thread).
 *
 * @param minimumInterval The least time between the start of one check and a re-check.
 */
public class SunsetCheckScheduler(minimumInterval: Duration = DEFAULT_MINIMUM_INTERVAL) {
    /** The app's state, as iOS's `ScenePhase`. */
    public enum class Phase {
        /** In the foreground and interactive (Android `ON_RESUME`). */
        ACTIVE,

        /** Visible or on its way back, but not interactive (any other Lifecycle event, e.g. `ON_PAUSE`, `ON_START`). */
        INACTIVE,

        /** No longer visible (Android `ON_STOP`). */
        BACKGROUND,
    }

    /** The least time between the start of one check and a re-check. [Duration.ZERO] re-checks on every return. */
    public var minimumInterval: Duration = minimumInterval

    /** Whether the launch check has started. */
    public var hasLaunched: Boolean = false
        private set

    /** Whether a check is running. */
    public var isChecking: Boolean = false
        private set

    /**
     * The `now` the last check that did not fail started at (the running one counts until it fails), or `null` before
     * the first.
     */
    public var lastCheckStarted: Duration? = null
        private set

    /** How many checks in a row have failed. Above `0`, a return from the background ignores [minimumInterval]. */
    public var failuresInARow: Int = 0
        private set

    /** Whether the quick retry after a first failure is due ([shouldRetry]). */
    public var isRetryPending: Boolean = false
        private set

    /** [lastCheckStarted] before the running check started, restored if it fails. */
    private var startBeforeRunningCheck: Duration? = null

    /** Whether the app went to the background since it was last active. */
    public var wasInBackground: Boolean = false
        private set

    /**
     * Call on the first composition or appear. `true` (start a check) only the first time.
     *
     * @param now A monotonic clock reading, e.g. `SystemClock.elapsedRealtime().milliseconds`.
     */
    public fun shouldCheckAtLaunch(now: Duration): Boolean {
        if (hasLaunched) return false
        hasLaunched = true
        return start(now)
    }

    /**
     * Call on every phase change. `true` (start a check) only for a return from the background, with no check in
     * flight and [minimumInterval] passed (or the last check failed).
     *
     * @param now A monotonic clock reading, the same clock as for [shouldCheckAtLaunch].
     */
    public fun shouldCheck(phase: Phase, now: Duration): Boolean = when (phase) {
        Phase.BACKGROUND -> {
            wasInBackground = true
            false
        }
        Phase.ACTIVE -> returnedFromBackground(now)
        // INACTIVE sits between BACKGROUND and ACTIVE on the way back, so it keeps wasInBackground.
        Phase.INACTIVE -> false
    }

    /**
     * Call when the check started by a `true` answer has finished; [failed] when it found no usable config
     * ([SunsetCheckOutcome.failed]).
     *
     * @return `true` when the caller should schedule the one quick retry, then call [shouldRetry] after its delay.
     */
    public fun checkFinished(failed: Boolean = false): Boolean {
        isChecking = false
        if (!failed) {
            failuresInARow = 0
            return false
        }
        lastCheckStarted = startBeforeRunningCheck
        failuresInARow += 1
        isRetryPending = failuresInARow == 1
        return isRetryPending
    }

    /**
     * Call when the retry delay asked for by [checkFinished] has passed. `true` (start the retry) only if no other
     * check has started since.
     *
     * @param now A monotonic clock reading, the same clock as for [shouldCheckAtLaunch].
     */
    public fun shouldRetry(now: Duration): Boolean {
        if (!isRetryPending) return false
        return start(now)
    }

    private fun returnedFromBackground(now: Duration): Boolean {
        if (!wasInBackground) return false
        wasInBackground = false
        // Before the launch check (an app started in the background), the first composition does the check.
        if (!hasLaunched) return false
        val last = lastCheckStarted
        if (failuresInARow == 0 && last != null && now - last < minimumInterval) return false
        return start(now)
    }

    private fun start(now: Duration): Boolean {
        if (isChecking) return false
        isChecking = true
        isRetryPending = false
        startBeforeRunningCheck = lastCheckStarted
        lastCheckStarted = now
        return true
    }

    public companion object {
        /** The default [minimumInterval]: one hour. */
        public val DEFAULT_MINIMUM_INTERVAL: Duration = 1.hours
    }
}
