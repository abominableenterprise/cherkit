package com.abominableenterprise.cherkit.sunset

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs every case in `fixtures/sunset/test-build-prompt.json`; Swift's `SunsetTestBuildPromptFixtureTests` runs the
 * same cases.
 */
class SunsetTestBuildPromptFixtureTest {
    @Serializable
    private data class PromptCase(
        val name: String,
        val decision: String,
        val kind: String? = null,
        val latestBuild: String? = null,
        val rememberedBuild: String? = null,
        val shouldShow: Boolean,
        val action: String? = null,
        val rememberedAfter: String? = null,
    )

    @Serializable
    private data class PromptFixture(val cases: List<PromptCase>)

    @Test
    fun promptCases() {
        val fixture = SunsetFixtures.json.decodeFromString<PromptFixture>(SunsetFixtures.text("test-build-prompt.json"))
        assertTrue(fixture.cases.isNotEmpty())
        for (case in fixture.cases) {
            val prompt = case.kind?.let {
                SunsetPrompt(
                    storeUrl = SunsetTestFlightConfig.DEFAULT_URL,
                    message = null,
                    kind = kind(it, case.name),
                    latestBuild = case.latestBuild?.let { build -> assertNotNull(SunsetBuildNumber.parse(build), case.name) },
                )
            }
            val decision = when (case.decision) {
                "softUpdate" -> SunsetDecision.SoftUpdate(assertNotNull(prompt, case.name))
                "hardUpdate" -> SunsetDecision.HardUpdate(assertNotNull(prompt, case.name))
                "upToDate" -> SunsetDecision.UpToDate
                else -> fail("unknown decision ${case.decision} in ${case.name}")
            }
            assertEquals(case.shouldShow, SunsetTestBuildPromptRule.shouldShow(decision, case.rememberedBuild), case.name)
            val action = case.action ?: continue
            val actionValue = when (action) {
                "notNow" -> SunsetPromptAction.NOT_NOW
                "update" -> SunsetPromptAction.UPDATE
                else -> fail("unknown action $action in ${case.name}")
            }
            assertEquals(
                case.rememberedAfter,
                SunsetTestBuildPromptRule.buildToRemember(actionValue, assertNotNull(prompt, case.name)),
                case.name,
            )
        }
    }
}
