package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.Instant as JavaInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/**
 * Runs every case in `fixtures/sunset/update-sources.json`; Swift's `SunsetUpdateSourcesFixtureTests` runs the same
 * cases.
 */
class SunsetUpdateSourcesFixtureTest {
    @Serializable
    private data class Defaults(
        val platform: String,
        val channel: String,
        val appVersion: String,
        val osVersion: String,
        val languages: List<String>,
        val now: String,
    )

    @Serializable
    private data class SourcesCase(
        val name: String,
        val config: String? = null,
        val lookup: String? = null,
        val platform: String? = null,
        val channel: String? = null,
        val installedBuild: String? = null,
        val appVersion: String? = null,
        val osVersion: String? = null,
        val languages: List<String>? = null,
        val now: String? = null,
        val decision: String,
        val kind: String? = null,
        val storeURL: String? = null,
        val message: String? = null,
        val latestVersion: String? = null,
        val latestBuild: String? = null,
    )

    @Serializable
    private data class SourcesFixture(
        val configs: Map<String, JsonObject>,
        val lookups: Map<String, JsonElement>,
        val defaults: Defaults,
        val cases: List<SourcesCase>,
    )

    @Test
    fun updateSourcesCases() {
        val fixture = SunsetFixtures.json.decodeFromString<SourcesFixture>(SunsetFixtures.text("update-sources.json"))
        val defaults = fixture.defaults
        assertTrue(fixture.cases.isNotEmpty())
        for (case in fixture.cases) {
            val config = case.config?.let { assertNotNull(fixture.configs[it], case.name).toString() }
            val lookup = case.lookup?.let { assertNotNull(fixture.lookups[it], case.name).toString() }
            val platformKey = case.platform ?: defaults.platform
            val decision = SunsetDecider.decide(
                config = config?.let(SunsetConfig::parse),
                platform = assertNotNull(SunsetPlatform.entries.find { it.key == platformKey }, case.name),
                appVersion = case.appVersion ?: defaults.appVersion,
                osVersion = case.osVersion ?: defaults.osVersion,
                languages = case.languages ?: defaults.languages,
                channel = channel(case.channel ?: defaults.channel, case.name),
                installedBuild = case.installedBuild,
                lookup = lookup?.let(SunsetLookupResult::parse),
                now = fixtureTime(case.now ?: defaults.now),
            )
            assertEquals(expected(case), decision, case.name)
        }
    }

    private fun expected(case: SourcesCase): SunsetDecision {
        if (case.decision == "upToDate") return SunsetDecision.UpToDate
        val prompt = SunsetPrompt(
            storeUrl = assertNotNull(case.storeURL, case.name),
            message = case.message,
            latestVersion = case.latestVersion?.let { assertNotNull(AppVersion.parse(it), case.name) },
            kind = kind(assertNotNull(case.kind, case.name), case.name),
            latestBuild = case.latestBuild?.let { assertNotNull(SunsetBuildNumber.parse(it), case.name) },
        )
        return when (case.decision) {
            "softUpdate" -> SunsetDecision.SoftUpdate(prompt)
            "hardUpdate" -> SunsetDecision.HardUpdate(prompt)
            else -> fail("unknown decision ${case.decision} in ${case.name}")
        }
    }
}

/** The fixtures' camelCase channel names. */
internal fun channel(name: String, case: String): SunsetChannel = when (name) {
    "store" -> SunsetChannel.STORE
    "testFlight" -> SunsetChannel.TEST_FLIGHT
    "other" -> SunsetChannel.OTHER
    else -> fail("unknown channel $name in $case")
}

/** The fixtures' camelCase kind names. */
internal fun kind(name: String, case: String): SunsetUpdateKind = when (name) {
    "update" -> SunsetUpdateKind.UPDATE
    "testBuild" -> SunsetUpdateKind.TEST_BUILD
    else -> fail("unknown kind $name in $case")
}

/** Parses the fixtures' `now` values independently of the code under test. */
internal fun fixtureTime(text: String): Instant {
    val parsed = JavaInstant.parse(text)
    return Instant.fromEpochSeconds(parsed.epochSecond, parsed.nano)
}
