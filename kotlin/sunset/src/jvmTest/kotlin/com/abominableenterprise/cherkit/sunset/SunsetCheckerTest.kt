package com.abominableenterprise.cherkit.sunset

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The checker's deadline and failure handling, the fetch source and the key-value cache. Fixture cases: [SunsetCheckerFixtureTest]. */
class SunsetCheckerTest {
    private val hard = """{"schemaVersion": 1, "android": {"minimumVersion": "2.0", "storeURL": "https://play.google.com/store/apps/details?id=a"}}"""
    private val soft = """{"schemaVersion": 1, "android": {"latestVersion": "2.0", "storeURL": "https://play.google.com/store/apps/details?id=b"}}"""

    @Test
    fun blockingSourceThatIgnoresItsTimeoutIsCutOffAndTheCacheIsUsed() = runBlocking<Unit> {
        val cache = MemorySunsetCache(soft)
        val blocking = SunsetConfigSource { Thread.sleep(3_000); hard }
        val start = TimeSource.Monotonic.markNow()
        val decision = SunsetChecker(blocking, cache, 200.milliseconds).check("1.0", "34", listOf("en"))
        assertTrue(start.elapsedNow() < 2.seconds, "took ${start.elapsedNow()}")
        assertIs<SunsetDecision.SoftUpdate>(decision)
        assertEquals(emptyList(), cache.saved)
    }

    @Test
    fun fetchWithinTheTimeoutIsUsedAndCached() = runBlocking<Unit> {
        val cache = MemorySunsetCache(soft)
        val source = FetchSunsetConfigSource { delay(20); hard }
        assertIs<SunsetDecision.HardUpdate>(SunsetChecker(source, cache, 5.seconds).check("1.0", "34", emptyList()))
        assertEquals(listOf(hard), cache.saved)
    }

    @Test
    fun nonPositiveTimeoutFailsOpen() = runBlocking<Unit> {
        for (timeout in listOf(Duration.ZERO, (-1).seconds)) {
            val decision = SunsetChecker(FetchSunsetConfigSource { hard }, MemorySunsetCache(), timeout).check("1.0", "34", emptyList())
            assertEquals(SunsetDecision.UpToDate, decision, "$timeout")
        }
    }

    @Test
    fun throwingSourceAndCacheFailOpen() = runBlocking<Unit> {
        val failingCache = object : SunsetConfigCache {
            override suspend fun load(): String = throw IllegalStateException("disk")
            override suspend fun save(text: String) = throw IllegalStateException("disk")
        }
        val throwingSource = SunsetConfigSource { throw IllegalStateException("bug") }
        assertEquals(SunsetDecision.UpToDate, SunsetChecker(throwingSource, failingCache).check("1.0", "34", emptyList()))
        // A source that leaks a TimeoutCancellationException of its own is a failure, not a cancellation of the caller.
        val leaking = SunsetConfigSource { withTimeout(1.milliseconds) { delay(1_000) }; hard }
        assertIs<SunsetDecision.SoftUpdate>(SunsetChecker(leaking, MemorySunsetCache(soft)).check("1.0", "34", emptyList()))
        assertIs<SunsetDecision.HardUpdate>(SunsetChecker(FetchSunsetConfigSource { hard }, failingCache).check("1.0", "34", emptyList()))
    }

    @Test
    fun callerCancellationPropagates() {
        val error = runCatching {
            runBlocking {
                withTimeout(100.milliseconds) {
                    SunsetChecker(FetchSunsetConfigSource { delay(10_000); hard }, MemorySunsetCache(), 30.seconds)
                        .check("1.0", "34", emptyList())
                }
            }
        }.exceptionOrNull()
        assertIs<TimeoutCancellationException>(error)
    }

    @Test
    fun fetchSourceTreatsNullEmptyAndThrowingAsFailure() = runBlocking<Unit> {
        assertNull(FetchSunsetConfigSource { null }.fetch(1.seconds))
        assertNull(FetchSunsetConfigSource { "" }.fetch(1.seconds))
        assertNull(FetchSunsetConfigSource { throw java.io.IOException("offline") }.fetch(1.seconds))
        assertNull(FetchSunsetConfigSource { throw CancellationException("source gave up") }.fetch(1.seconds))
        assertNull(FetchSunsetConfigSource { delay(5_000); hard }.fetch(50.milliseconds))
        assertEquals(hard, FetchSunsetConfigSource { hard }.fetch(1.seconds))
    }

    @Test
    fun bodyIfSuccessAcceptsOnlyNonEmpty2xx() {
        assertEquals("x", SunsetFetch.bodyIfSuccess(200, "x"))
        assertEquals("x", SunsetFetch.bodyIfSuccess(299, "x"))
        for (status in listOf(100, 199, 300, 304, 404, 500)) assertNull(SunsetFetch.bodyIfSuccess(status, "x"), "$status")
        assertNull(SunsetFetch.bodyIfSuccess(200, ""))
        assertNull(SunsetFetch.bodyIfSuccess(200, null))
    }

    @Test
    fun keyValueCacheRoundTripsUnderItsKeyAndSwallowsStorageErrors() = runBlocking<Unit> {
        val store = MapSunsetStore()
        val cache = KeyValueSunsetConfigCache(store, key = "test.config")
        assertNull(cache.load())
        cache.save(soft)
        assertEquals(soft, cache.load())
        assertEquals(mapOf("test.config" to soft), store.values)
        assertNull(KeyValueSunsetConfigCache(store).load())

        val broken = object : SunsetKeyValueStore {
            override fun getString(key: String): String = throw IllegalStateException("corrupt")
            override fun putString(key: String, value: String) = throw IllegalStateException("full")
        }
        assertNull(KeyValueSunsetConfigCache(broken).load())
        KeyValueSunsetConfigCache(broken).save(soft)
    }

    @Test
    fun malformedFetchIsNeverWrittenToTheStore() = runBlocking<Unit> {
        val store = MapSunsetStore(mutableMapOf(KeyValueSunsetConfigCache.DEFAULT_KEY to soft))
        val checker = SunsetChecker(FetchSunsetConfigSource { "<html>oops</html>" }, KeyValueSunsetConfigCache(store))
        assertIs<SunsetDecision.SoftUpdate>(checker.check("1.0", "34", emptyList()))
        assertEquals(mapOf(KeyValueSunsetConfigCache.DEFAULT_KEY to soft), store.values)
        assertEquals(5.seconds, checker.timeout)
        assertEquals(SunsetPlatform.ANDROID, checker.platform)
    }
}
