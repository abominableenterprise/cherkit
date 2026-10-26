package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs `fixtures/whatsnew/locale.json`; Swift's `LocaleFallbackFixtureTests` runs the same cases. */
class LocaleFallbackFixtureTest {
    @Serializable
    private data class Fixture(val cases: List<Case>)

    @Serializable
    private data class Case(val name: String, val preferred: List<String>, val available: List<String>, val expected: String?)

    @Test
    fun cases() {
        val cases = loadFixture<Fixture>("locale.json").cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            assertEquals(case.expected, LocaleFallback.resolve(case.preferred, case.available), case.name)
        }
    }

    @Test
    fun bulletsUseFallback() {
        val notes = ReleaseNotes(AppVersion.parse("1.0")!!, notes = mapOf("en" to listOf("Hi."), "pt" to listOf("Olá.")))
        assertEquals(listOf("Olá."), notes.bullets(listOf("pt-BR")))
        assertEquals(listOf("Hi."), notes.bullets(listOf("ja")))
    }
}
