package com.abominableenterprise.cherkit.sunset

import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How [SunsetChecker] asks its optional App Store lookup: only on the store channel, within the same deadline. */
class SunsetCheckerLookupTest {
    private val config =
        """{"schemaVersion": 1, "ios": {"latestVersion": "1.4", "storeURL": "https://apps.apple.com/app/id1", "testFlight": {"latestBuild": "9"}}}"""
    private val lookupBody = """{"resultCount": 1, "results": [{"version": "2.0", "minimumOsVersion": "15.0"}]}"""

    private fun checker(lookup: SunsetLookupSource?, timeout: Duration = 5.seconds) =
        SunsetChecker(SunsetConfigSource { config }, MemorySunsetCache(), timeout, SunsetPlatform.IOS, lookup)

    @Test
    fun lookupVersionIsOfferedOnTheStoreChannel() = runBlocking<Unit> {
        val calls = AtomicInteger()
        val decision = checker(SunsetLookupSource { calls.incrementAndGet(); lookupBody }).check("1.4", "17.0", listOf("en"))
        assertEquals("2.0", assertIs<SunsetDecision.SoftUpdate>(decision).prompt.latestVersion.toString())
        assertEquals(1, calls.get())
    }

    @Test
    fun lookupIsNotAskedOnTestFlight() = runBlocking<Unit> {
        val calls = AtomicInteger()
        val decision = checker(SunsetLookupSource { calls.incrementAndGet(); lookupBody })
            .check("1.4", "17.0", listOf("en"), SunsetChannel.TEST_FLIGHT, "8")
        val prompt = assertIs<SunsetDecision.SoftUpdate>(decision).prompt
        assertEquals(SunsetUpdateKind.TEST_BUILD, prompt.kind)
        assertEquals(SunsetBuildNumber.parse("9"), prompt.latestBuild)
        assertEquals(0, calls.get())
    }

    @Test
    fun blockingLookupIsCutOffAndTheConfigStillApplies() = runBlocking<Unit> {
        val start = TimeSource.Monotonic.markNow()
        val decision = checker(SunsetLookupSource { Thread.sleep(3_000); lookupBody }, 200.milliseconds)
            .check("1.3", "17.0", listOf("en"))
        assertTrue(start.elapsedNow() < 2.seconds, "took ${start.elapsedNow()}")
        assertEquals("1.4", assertIs<SunsetDecision.SoftUpdate>(decision).prompt.latestVersion.toString())
    }

    @Test
    fun throwingLookupFailsOpen() = runBlocking<Unit> {
        val decision = checker(SunsetLookupSource { error("network") }).check("1.4", "17.0", listOf("en"))
        assertEquals(SunsetDecision.UpToDate, decision)
    }
}
