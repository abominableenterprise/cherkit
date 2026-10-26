package com.abominableenterprise.cherkit.sunset

/**
 * What [SunsetChecker.checkReportingFailure] returns: the [decision] that [SunsetChecker.check] would give, plus
 * whether the check [failed].
 *
 * Most apps never need it: [SunsetChecker.check] gives the same decision. It exists for code that schedules checks,
 * such as sunset-compose's `SunsetPrompt(checker = ...)`, which retries a failed check and does not count it toward
 * `minimumInterval` (see [SunsetCheckScheduler.checkFinished]).
 */
public data class SunsetCheckOutcome(
    /** The decision, the same as [SunsetChecker.check]'s. [SunsetDecision.UpToDate] when [failed] (fail open). */
    public val decision: SunsetDecision,
    /**
     * `true` when there was no usable config at all: the fetch failed, timed out or did not parse, and nothing
     * usable was cached. The fail-open [decision] is then not a real answer. Never `true` for [SunsetChecker.disabled].
     */
    public val failed: Boolean,
)
