package com.abominableenterprise.cherkit.sunset

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What app code (and app tests) read from [SunsetChecker]: the public [SunsetChecker.isDisabled], and the `failed` flag
 * of [SunsetChecker.checkReportingFailure] that sunset-compose uses to retry. The same cases as Swift's
 * `SunsetCheckerPublicAPITests`.
 */
class SunsetCheckOutcomeTest {
    private val hard = """{"schemaVersion": 1, "android": {"minimumVersion": "2.0", "storeURL": "https://play.google.com/store/apps/details?id=a"}}"""

    private class Case(val fetched: String?, val cached: String?, val failed: Boolean, val label: String)

    @Test
    fun isDisabledIsPublicAndTrueOnlyForTheDisabledChecker() {
        assertTrue(SunsetChecker.disabled().isDisabled)
        assertFalse(SunsetChecker(FetchSunsetConfigSource { null }, MemorySunsetCache()).isDisabled)
        // Even a checker built from the same do-nothing parts is not the disabled one.
        val lookalike = SunsetChecker.disabled()
        assertFalse(SunsetChecker(lookalike.source, lookalike.cache).isDisabled)
    }

    @Test
    fun checkFailsOnlyWithNoUsableConfig() = runBlocking<Unit> {
        val cases = listOf(
            Case(null, null, failed = true, "fetch failed, nothing cached"),
            Case("""{"schemaVersion": 1, "android":""", null, failed = true, "truncated body, nothing cached"),
            Case(null, "not json", failed = true, "fetch failed, cache unusable"),
            Case(null, hard, failed = false, "fetch failed, cache usable"),
            Case(hard, null, failed = false, "fetched"),
        )
        for (case in cases) {
            val checker = SunsetChecker(FetchSunsetConfigSource { case.fetched }, MemorySunsetCache(case.cached))
            val outcome = checker.checkReportingFailure("1.0", "34", listOf("en"))
            assertEquals(case.failed, outcome.failed, case.label)
            val decision = checker.check("1.0", "34", listOf("en"))
            assertEquals(decision, outcome.decision, case.label)
            if (case.failed) assertEquals(SunsetDecision.UpToDate, decision, "fails open: ${case.label}")
        }
    }

    @Test
    fun disabledNeverFails() = runBlocking<Unit> {
        val outcome = SunsetChecker.disabled().checkReportingFailure("1.0", "34", emptyList())
        assertEquals(SunsetCheckOutcome(SunsetDecision.UpToDate, failed = false), outcome)
    }
}
