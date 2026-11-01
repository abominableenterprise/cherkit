package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Runs every case in `fixtures/sunset/lookup.json`; Swift's `SunsetLookupFixtureTests` runs the same cases. */
class SunsetLookupFixtureTest {
    @Serializable
    private data class Expected(val version: String, val minimumOsVersion: String, val trackViewUrl: String? = null)

    @Serializable
    private data class LookupCase(
        val name: String,
        val body: JsonElement? = null,
        val text: String? = null,
        val result: Expected? = null,
    )

    @Serializable
    private data class LookupFixture(val cases: List<LookupCase>)

    @Test
    fun lookupCases() {
        val fixture = SunsetFixtures.json.decodeFromString<LookupFixture>(SunsetFixtures.text("lookup.json"))
        assertTrue(fixture.cases.isNotEmpty())
        for (case in fixture.cases) {
            val body = case.text ?: assertNotNull(case.body, case.name).toString()
            val expected = case.result?.let {
                SunsetLookupResult(
                    version = assertNotNull(AppVersion.parse(it.version), case.name),
                    minimumOsVersion = assertNotNull(AppVersion.parse(it.minimumOsVersion), case.name),
                    trackViewUrl = it.trackViewUrl,
                )
            }
            assertEquals(expected, SunsetLookupResult.parse(body), case.name)
        }
    }
}
