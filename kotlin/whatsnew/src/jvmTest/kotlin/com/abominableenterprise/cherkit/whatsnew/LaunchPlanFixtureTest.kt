package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs `fixtures/whatsnew/launch.json`; Swift's `LaunchPlanFixtureTests` runs the same cases. */
class LaunchPlanFixtureTest {
    @Serializable
    private data class Fixture(val cases: List<Case>)

    @Serializable
    private data class Case(val name: String, val notes: List<Note>, val installed: String, val lastSeen: String?, val expected: Expected)

    @Serializable
    private data class Note(val version: String, val show: Boolean)

    @Serializable
    private data class Expected(val show: List<String>, val lastSeen: String?)

    @Test
    fun cases() {
        val cases = loadFixture<Fixture>("launch.json").cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            // Each note's bullet records its version as written, so duplicates can be told apart.
            val notes = case.notes.map { ReleaseNotes(AppVersion.parse(it.version)!!, show = it.show, notes = mapOf("en" to listOf(it.version))) }
            val plan = LaunchPlan.make(notes, case.installed, case.lastSeen)
            assertEquals(case.expected.show, plan.show.map { it.notes.getValue("en").first() }, case.name)
            assertEquals(case.expected.lastSeen, plan.lastSeen, case.name)
        }
    }
}
