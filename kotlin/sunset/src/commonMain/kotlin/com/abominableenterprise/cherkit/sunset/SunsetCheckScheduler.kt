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
 * - Re-checks wait at least [minimumInterval] since the last check started. The launch check always runs.
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

    /** The `now` the last check started at, or `null` before the first. */
    public var lastCheckStarted: Duration? = null
        private set

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
     * flight and [minimumInterval] passed.
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

    /** Call when the check started by a `true` answer has finished. */
    public fun checkFinished() {
        isChecking = false
    }

    private fun returnedFromBackground(now: Duration): Boolean {
        if (!wasInBackground) return false
        wasInBackground = false
        // Before the launch check (an app started in the background), the first composition does the check.
        if (!hasLaunched) return false
        val last = lastCheckStarted
        if (last != null && now - last < minimumInterval) return false
        return start(now)
    }

    private fun start(now: Duration): Boolean {
        if (isChecking) return false
        isChecking = true
        lastCheckStarted = now
        return true
    }

    public companion object {
        /** The default [minimumInterval]: one hour. */
        public val DEFAULT_MINIMUM_INTERVAL: Duration = 1.hours
    }
}
