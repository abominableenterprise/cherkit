package com.abominableenterprise.cherkit.review

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Wall-clock epoch milliseconds, the default clock of [ReviewCoordinator]. */
@OptIn(ExperimentalTime::class)
internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
