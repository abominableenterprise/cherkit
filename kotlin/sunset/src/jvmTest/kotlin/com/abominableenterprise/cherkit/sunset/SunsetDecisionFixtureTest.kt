package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Runs every case in `fixtures/sunset/decision.json`; Swift's `SunsetDecisionFixtureTests` runs the same cases. */
class SunsetDecisionFixtureTest {
    @Serializable
    private data class DecisionFixture(val configs: Map<String, JsonObject>, val cases: List<DecisionCase>)

    @Serializable
    private data class DecisionCase(
        val name: String,
        val config: String? = null,
        val configText: String? = null,
        val platform: String,
        val appVersion: String,
        val osVersion: String,
        val languages: List<String>,
        val decision: String,
        val storeURL: String? = null,
        val latestVersion: String? = null,
        val message: String? = null,
    )

    @Test
    fun decisionCases() {
        val fixture = SunsetFixtures.json.decodeFromString<DecisionFixture>(SunsetFixtures.text("decision.json"))
        assertTrue(fixture.cases.isNotEmpty())
        for (case in fixture.cases) {
            val platform = assertNotNull(SunsetPlatform.entries.find { it.key == case.platform }, case.name)
            val body = case.config?.let { assertNotNull(fixture.configs[it], case.name).toString() } ?: case.configText
            val decision = SunsetDecider.decide(
                config = body?.let(SunsetConfig::parse),
                platform = platform,
                appVersion = case.appVersion,
                osVersion = case.osVersion,
                languages = case.languages,
            )
            assertEquals(expected(case), decision, case.name)
        }
    }

    private fun expected(case: DecisionCase): SunsetDecision {
        if (case.decision == "upToDate") return SunsetDecision.UpToDate
        val latest = case.latestVersion?.let { assertNotNull(AppVersion.parse(it), case.name) }
        val prompt = SunsetPrompt(assertNotNull(case.storeURL, case.name), case.message, latest)
        return when (case.decision) {
            "softUpdate" -> SunsetDecision.SoftUpdate(prompt)
            "hardUpdate" -> SunsetDecision.HardUpdate(prompt)
            else -> fail("unknown decision ${case.decision} in ${case.name}")
        }
    }
}
