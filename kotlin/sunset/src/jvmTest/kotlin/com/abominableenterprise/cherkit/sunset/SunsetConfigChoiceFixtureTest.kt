package com.abominableenterprise.cherkit.sunset

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs every case in `fixtures/sunset/config-choice.json`; Swift's `SunsetConfigChoiceFixtureTests` runs the same. */
class SunsetConfigChoiceFixtureTest {
    @Serializable
    private data class ChoiceFixture(val cases: List<ChoiceCase>)

    @Serializable
    private data class ChoiceCase(val name: String, val fetched: String?, val cached: String?, val source: String)

    @Test
    fun choiceCases() {
        val cases = SunsetFixtures.json.decodeFromString<ChoiceFixture>(SunsetFixtures.text("config-choice.json")).cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val choice = SunsetConfigChoice.choose(fetched = case.fetched, cached = case.cached)
            assertEquals(case.source, choice.source.name.lowercase(), case.name)
            val expectedBody = when (choice.source) {
                SunsetConfigChoice.Source.FETCHED -> case.fetched
                SunsetConfigChoice.Source.CACHED -> case.cached
                SunsetConfigChoice.Source.MISSING -> null
            }
            assertEquals(expectedBody?.let(SunsetConfig::parse), choice.config, case.name)
        }
    }
}
