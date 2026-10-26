package com.abominableenterprise.cherkit.sunset.compose

import com.abominableenterprise.cherkit.core.AppVersion
import com.abominableenterprise.cherkit.sunset.SunsetDecision
import com.abominableenterprise.cherkit.sunset.SunsetPrompt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs `fixtures/sunset/soft-prompt.json`: the "Not now" rule shared with iOS. */
class SunsetSoftPromptRuleFixtureTest {
    private data class Case(
        val name: String,
        val decision: String?,
        val latestVersion: String?,
        val installedVersion: String,
        val dismissedVersion: String?,
        val version: String,
        val show: Boolean,
    )

    private val cases: List<Case> = run {
        val dir = requireNotNull(System.getProperty("cherkit.fixtures")) { "cherkit.fixtures is not set; run through Gradle" }
        val root = Json.parseToJsonElement(File(dir, "sunset/soft-prompt.json").readText()).jsonObject
        root.getValue("cases").jsonArray.map { it.jsonObject.toCase() }
    }

    private fun JsonObject.string(key: String): String? = getValue(key).jsonPrimitive.contentOrNull

    private fun JsonObject.toCase() = Case(
        name = string("name")!!,
        decision = string("decision"),
        latestVersion = string("latestVersion"),
        installedVersion = string("installedVersion")!!,
        dismissedVersion = string("dismissedVersion"),
        version = string("version")!!,
        show = getValue("show").jsonPrimitive.boolean,
    )

    @Test
    fun everyCaseMatches() {
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val prompt = SunsetPrompt(
                storeUrl = "https://play.google.com/store/apps/details?id=com.example.app",
                message = null,
                latestVersion = case.latestVersion?.let { requireNotNull(AppVersion.parse(it)) { case.name } },
            )
            val decision = when (case.decision) {
                null -> null
                "upToDate" -> SunsetDecision.UpToDate
                "softUpdate" -> SunsetDecision.SoftUpdate(prompt)
                "hardUpdate" -> SunsetDecision.HardUpdate(prompt)
                else -> error("unknown decision ${case.decision} in ${case.name}")
            }
            val version = SunsetSoftPromptRule.version(decision?.prompt, case.installedVersion)
            assertEquals(case.version, version, case.name)
            assertEquals(case.show, SunsetSoftPromptRule.shouldShow(decision, version, case.dismissedVersion), case.name)
        }
    }
}
