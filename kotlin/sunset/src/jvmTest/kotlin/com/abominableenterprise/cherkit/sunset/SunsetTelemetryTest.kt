package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.TelemetrySink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SunsetTelemetryTest {
    private val good = """{"schemaVersion":1,"android":{"latestVersion":"2","storeURL":"https://example.com/update"}}"""
    private val secret = "private text https://example.com/private"

    @Serializable
    private data class Fixture(val domain: String, val breadcrumb: String, val cases: List<Failure>, val decisionKinds: List<String>)
    @Serializable
    private data class Failure(val operation: String, val result: String, val code: Int)

    @Test
    fun sharedFailureContract() = runBlocking {
        val fixture = SunsetFixtures.json.decodeFromString<Fixture>(SunsetFixtures.text("kotlin-telemetry.json"))
        assertEquals(listOf("none", "soft", "hard", "test_build"), fixture.decisionKinds)
        for (case in fixture.cases) {
            val sink = Sink()
            val fetch: suspend () -> String? = {
                when (case.result) {
                    "missing" -> null
                    "throws" -> error(secret)
                    "timeout" -> { delay(10_000); null }
                    "undecodable" -> secret
                    else -> error("unknown fixture result")
                }
            }
            val config = if (case.operation == "config") SunsetConfigSource { fetch() } else SunsetConfigSource { good }
            val lookup = if (case.operation == "lookup") SunsetLookupSource { fetch() } else null
            SunsetChecker(config, MemorySunsetCache(good), 100.milliseconds, lookup = lookup, telemetrySink = sink)
                .check("1", "34", emptyList())
            assertEquals(listOf(fixture.domain to case.code), sink.errors)
            assertEquals(listOf(fixture.breadcrumb), sink.breadcrumbs)
        }
    }

    @Test
    fun configFailuresAreReportedOnceEvenWithCachedSuccess() = runBlocking {
        val failures: List<Pair<Int, SunsetConfigSource>> = listOf(
            1 to SunsetConfigSource { null },
            1 to SunsetConfigSource { error(secret) },
            1 to SunsetConfigSource { throw CancellationException(secret) },
            2 to SunsetConfigSource { delay(10_000); good },
            2 to SunsetConfigSource { withTimeout(1.milliseconds) { delay(10_000) }; good },
            3 to SunsetConfigSource { secret },
        )
        for ((code, source) in failures) {
            val sink = Sink()
            val checker = SunsetChecker(source, MemorySunsetCache(good), 100.milliseconds, telemetrySink = sink)
            assertIs<SunsetDecision.SoftUpdate>(checker.check("1", "34", emptyList()))
            assertEquals(listOf("cherkit.sunset" to code), sink.errors)
            assertEquals(listOf(mapOf<String, Any>("kind" to "soft")), sink.attributes)
        }
    }

    @Test
    fun fetchAdapterDoesNotHideTimeoutOrDoubleReport() = runBlocking {
        val cases: List<Pair<Int, SunsetFetch>> = listOf(
            1 to SunsetFetch { null },
            1 to SunsetFetch { "" },
            1 to SunsetFetch { error(secret) },
            2 to SunsetFetch { delay(10_000); good },
            3 to SunsetFetch { secret },
        )
        for ((code, fetch) in cases) {
            val sink = Sink()
            SunsetChecker(FetchSunsetConfigSource(fetch), MemorySunsetCache(), 100.milliseconds, telemetrySink = sink)
                .check("1", "34", emptyList())
            assertEquals(listOf("cherkit.sunset" to code), sink.errors)
            assertEquals(listOf(mapOf<String, Any>("kind" to "none")), sink.attributes)
        }
    }

    @Test
    fun lookupFailuresAreReportedOnceAndOnlyOnStoreChannel() = runBlocking {
        val cases: List<Pair<Int, SunsetLookupSource>> = listOf(
            4 to SunsetLookupSource { null },
            4 to SunsetLookupSource { error(secret) },
            4 to SunsetLookupSource { throw CancellationException(secret) },
            5 to SunsetLookupSource { delay(10_000); "" },
            5 to SunsetLookupSource { withTimeout(1.milliseconds) { delay(10_000) }; "" },
            6 to SunsetLookupSource { secret },
            6 to SunsetLookupSource { """{"resultCount":0,"results":[]}""" },
        )
        for ((code, lookup) in cases) {
            val sink = Sink()
            val checker = SunsetChecker(SunsetConfigSource { good }, MemorySunsetCache(), 100.milliseconds,
                lookup = lookup, telemetrySink = sink)
            checker.check("1", "34", emptyList())
            assertEquals(listOf("cherkit.sunset" to code), sink.errors)
            sink.errors.clear()
            checker.check("1", "34", emptyList(), SunsetChannel.OTHER)
            assertTrue(sink.errors.isEmpty())
        }
    }

    @Test
    fun eachDecisionHasOneAllowlistedBreadcrumbAndSuccessHasNoErrors() = runBlocking {
        val configs = listOf(
            "none" to """{"schemaVersion":1}""",
            "soft" to good,
            "hard" to good.replace("latestVersion", "minimumVersion"),
            "test_build" to """{"schemaVersion":1,"ios":{"storeURL":"https://example.com/update","testFlight":{"latestBuild":"9"}}}""",
        )
        for ((kind, config) in configs) {
            val sink = Sink()
            SunsetChecker(SunsetConfigSource { config }, MemorySunsetCache(), platform =
                if (kind == "test_build") SunsetPlatform.IOS else SunsetPlatform.ANDROID, telemetrySink = sink)
                .check("1", "34", listOf(secret), SunsetChannel.TEST_FLIGHT, "1")
            assertEquals(emptyList(), sink.errors)
            assertEquals(listOf("cherkit.sunset.decision"), sink.breadcrumbs)
            assertEquals(listOf(mapOf<String, Any>("kind" to kind)), sink.attributes)
        }
    }

    @Test
    fun simultaneousFailuresEachHaveOneCode() = runBlocking {
        val sink = Sink()
        SunsetChecker(SunsetConfigSource { null }, MemorySunsetCache(), lookup = SunsetLookupSource { secret },
            telemetrySink = sink).check("1", "34", emptyList())
        assertEquals(listOf("cherkit.sunset" to 1, "cherkit.sunset" to 6), sink.errors)
    }

    @Test
    fun cancellationDoesNotReportAbandonedWork() = runBlocking {
        val sink = Sink()
        val result = runCatching {
            withTimeout(50.milliseconds) {
                SunsetChecker(SunsetConfigSource { delay(10_000); good }, MemorySunsetCache(), 5.seconds,
                    lookup = SunsetLookupSource { delay(10_000); "" }, telemetrySink = sink)
                    .check("1", "34", emptyList())
            }
        }
        assertIs<TimeoutCancellationException>(result.exceptionOrNull())
        assertTrue(sink.errors.isEmpty())
        assertTrue(sink.breadcrumbs.isEmpty())
    }

    @Test
    fun nullAndThrowingSinksDoNotChangeFailOpenBehavior() = runBlocking {
        for (sink in listOf(null, object : TelemetrySink {
            override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) { error(secret) }
            override fun breadcrumb(name: String, attributes: Map<String, Any>) { error(secret) }
        })) {
            assertEquals(SunsetDecision.UpToDate,
                SunsetChecker(SunsetConfigSource { null }, MemorySunsetCache(), telemetrySink = sink)
                    .check("1", "34", emptyList()))
        }
        assertEquals(SunsetDecision.UpToDate, SunsetChecker.disabled().check("1", "34", emptyList()))
    }

    private class Sink : TelemetrySink {
        val errors = mutableListOf<Pair<String, Int>>()
        val breadcrumbs = mutableListOf<String>()
        val attributes = mutableListOf<Map<String, Any>>()
        override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) {
            assertTrue(attributes.isEmpty())
            errors += domain to code
        }
        override fun breadcrumb(name: String, attributes: Map<String, Any>) {
            breadcrumbs += name
            this.attributes += attributes
        }
    }
}
