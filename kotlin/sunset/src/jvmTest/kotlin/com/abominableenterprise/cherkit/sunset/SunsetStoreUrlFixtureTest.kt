package com.abominableenterprise.cherkit.sunset

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs every case in `fixtures/sunset/store-url.json`; Swift's `SunsetStoreURLFixtureTests` runs the same cases. */
class SunsetStoreUrlFixtureTest {
    @Serializable
    private data class StoreUrlFixture(val cases: List<StoreUrlCase>)

    @Serializable
    private data class StoreUrlCase(val url: String, val valid: Boolean)

    @Test
    fun storeUrlCases() {
        val cases = SunsetFixtures.json.decodeFromString<StoreUrlFixture>(SunsetFixtures.text("store-url.json")).cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val body = buildJsonObject {
                put("schemaVersion", 1)
                put("ios", buildJsonObject {
                    put("latestVersion", "2.0")
                    put("storeURL", JsonPrimitive(case.url))
                })
            }
            val block = SunsetConfig.parse(body.toString())?.ios
            assertEquals(case.valid, block != null, "\"${case.url}\"")
            block?.let { assertEquals(case.url, it.storeUrl, "\"${case.url}\"") }
        }
    }
}
