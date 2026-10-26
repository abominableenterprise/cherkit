package com.abominableenterprise.cherkit.whatsnew

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs `fixtures/whatsnew/install-history.json`; Swift's `InstallHistoryFixtureTests` runs the same cases. */
class InstallHistoryFixtureTest {
    @Serializable
    private data class Fixture(val cases: List<Case>)

    @Serializable
    private data class Case(val name: String, val history: List<String>, val current: String, val cap: Int? = null, val expected: List<String>)

    @Test
    fun cases() {
        val cases = loadFixture<Fixture>("install-history.json").cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val result = case.cap?.let { InstallHistory.updated(case.history, case.current, it) }
                ?: InstallHistory.updated(case.history, case.current)
            assertEquals(case.expected, result, case.name)
        }
    }

    @Test
    fun defaultCapIs100() {
        val result = InstallHistory.updated((0 until 100).map { "1.$it" }, "2.0")
        assertEquals(100, result.size)
        assertEquals("1.1", result.first())
        assertEquals("2.0", result.last())
    }
}
