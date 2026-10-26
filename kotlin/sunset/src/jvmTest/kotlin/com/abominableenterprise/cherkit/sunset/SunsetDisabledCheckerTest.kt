package com.abominableenterprise.cherkit.sunset

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/** [SunsetChecker.disabled]: always `UpToDate`, with no network and no cache. */
class SunsetDisabledCheckerTest {
    @Test
    fun alwaysUpToDate() = runBlocking<Unit> {
        for ((installed, os) in listOf("0.0.1" to "1", "1.0" to "34", "" to "", "dev" to "unknown")) {
            assertEquals(SunsetDecision.UpToDate, SunsetChecker.disabled().check(installed, os, listOf("en")), "$installed on $os")
        }
    }

    @Test
    fun touchesNeitherTheNetworkNorACache() = runBlocking<Unit> {
        val checker = SunsetChecker.disabled()
        assertSame(checker, SunsetChecker.disabled())
        assertNull(checker.source.fetch(1.seconds))
        assertNull(checker.cache.load())
        checker.cache.save("{}")
        assertNull(checker.cache.load())
    }

    @Test
    fun ordinaryCheckersAreNotDisabled() = runBlocking<Unit> {
        val hard = """{"schemaVersion": 1, "android": {"minimumVersion": "2.0", "storeURL": "https://play.google.com/store/apps/details?id=a"}}"""
        val checker = SunsetChecker(FetchSunsetConfigSource { hard }, MemorySunsetCache())
        assertIs<SunsetDecision.HardUpdate>(checker.check("1.0", "34", listOf("en")))
    }
}
