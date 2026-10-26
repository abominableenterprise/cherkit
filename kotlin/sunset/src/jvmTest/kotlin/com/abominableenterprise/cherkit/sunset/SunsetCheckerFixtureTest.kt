package com.abominableenterprise.cherkit.sunset

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Runs every case in `fixtures/sunset/checker.json` through [SunsetChecker] with [FetchSunsetConfigSource] over a
 * fake HTTP call; Swift's `SunsetCheckerFixtureTests` runs the same cases.
 */
class SunsetCheckerFixtureTest {
    @Serializable
    private data class CheckerFixture(val configs: Map<String, JsonObject>, val cases: List<CheckerCase>, val defaults: Defaults)

    @Serializable
    private data class Defaults(val installedVersion: String, val osVersion: String, val locale: List<String>)

    @Serializable
    private data class Fetch(val result: String, val status: Int? = null, val config: String? = null, val text: String? = null)

    @Serializable
    private data class CheckerCase(
        val name: String,
        val platform: String,
        val installedVersion: String? = null,
        val osVersion: String? = null,
        val fetch: Fetch,
        val cached: String? = null,
        val cachedText: String? = null,
        val decision: String,
        val storeURL: String? = null,
        val cacheAfter: String,
    )

    @Test
    fun checkerCases() = runBlocking<Unit> {
        val fixture = SunsetFixtures.json.decodeFromString<CheckerFixture>(SunsetFixtures.text("checker.json"))
        fun body(name: String?, text: String?): String? = name?.let { fixture.configs.getValue(it).toString() } ?: text
        assertTrue(fixture.cases.isNotEmpty())

        for (case in fixture.cases) {
            val fetchedBody = body(case.fetch.config, case.fetch.text)
            val fetch = when (case.fetch.result) {
                "response" -> SunsetFetch { SunsetFetch.bodyIfSuccess(assertNotNull(case.fetch.status), fetchedBody) }
                "timeout" -> SunsetFetch { awaitCancellation() }
                "error" -> SunsetFetch { throw IOException("offline") }
                else -> error("unknown fetch result in ${case.name}")
            }
            val cached = body(case.cached, case.cachedText)
            val cache = MemorySunsetCache(cached)
            val checker = SunsetChecker(
                source = FetchSunsetConfigSource(fetch),
                cache = cache,
                timeout = if (case.fetch.result == "timeout") 200.milliseconds else 10.seconds,
                platform = assertNotNull(SunsetPlatform.entries.find { it.key == case.platform }, case.name),
            )

            val decision = checker.check(
                installedVersion = case.installedVersion ?: fixture.defaults.installedVersion,
                osVersion = case.osVersion ?: fixture.defaults.osVersion,
                locale = fixture.defaults.locale,
            )

            assertEquals(case.decision, decision.name(), case.name)
            assertEquals(case.storeURL, decision.prompt?.storeUrl, case.name)
            val saved = if (case.cacheAfter == "fetched") listOf(assertNotNull(fetchedBody, case.name)) else emptyList()
            assertEquals(saved, cache.saved, case.name)
            assertEquals(if (case.cacheAfter == "fetched") fetchedBody else cached, cache.load(), case.name)
        }
    }

    private fun SunsetDecision.name(): String = when (this) {
        SunsetDecision.UpToDate -> "upToDate"
        is SunsetDecision.SoftUpdate -> "softUpdate"
        is SunsetDecision.HardUpdate -> "hardUpdate"
    }
}
