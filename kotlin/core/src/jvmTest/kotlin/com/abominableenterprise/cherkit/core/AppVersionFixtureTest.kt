package com.abominableenterprise.cherkit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Runs every case in `fixtures/core/version.json`; Swift's `AppVersionFixtureTests` runs the same cases. */
class AppVersionFixtureTest {
    @Serializable
    private data class VersionFixture(val parse: List<ParseCase>, val compare: List<CompareCase>)

    /** `components == null` means the input is malformed and must not parse. */
    @Serializable
    private data class ParseCase(val input: String, val components: List<Int>?)

    @Serializable
    private data class CompareCase(val a: String, val b: String, val result: Int)

    private val fixture: VersionFixture by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString(File(fixturesDir(), "core/version.json").readText())
    }

    @Test
    fun parseCases() {
        assertTrue(fixture.parse.isNotEmpty())
        for (case in fixture.parse) {
            assertEquals(case.components, AppVersion.parse(case.input)?.components, "parse \"${case.input}\"")
        }
    }

    @Test
    fun compareCases() {
        assertTrue(fixture.compare.isNotEmpty())
        for (case in fixture.compare) {
            val label = "${case.a} vs ${case.b}"
            val a = assertNotNull(AppVersion.parse(case.a), label)
            val b = assertNotNull(AppVersion.parse(case.b), label)
            assertEquals(case.result, a.compareTo(b), label)
            assertEquals(-case.result, b.compareTo(a), "$label, reversed")
            assertEquals(case.result == 0, a == b, "$label, equals")
            if (case.result == 0) assertEquals(a.hashCode(), b.hashCode(), "$label, hashCode")
        }
    }

    @Test
    fun toStringJoinsComponents() {
        assertEquals("1.2.3", AppVersion.parse(" v1.02.3 (45) ").toString())
    }

    /** The root build sets `cherkit.fixtures` on every Test task to the repo's `fixtures/` folder. */
    private fun fixturesDir(): File = File(requireNotNull(System.getProperty("cherkit.fixtures")) {
        "cherkit.fixtures is not set; run the tests through Gradle"
    })
}
