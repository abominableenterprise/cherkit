package com.abominableenterprise.cherkit.whatsnew

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs `fixtures/whatsnew/submission-gate.json`; Swift's `SubmissionGateFixtureTests` runs the same cases. */
class SubmissionGateFixtureTest {
    @Serializable
    private data class Fixture(val cases: List<Case>)

    @Serializable
    private data class Case(val name: String, val version: String, val files: Map<String, JsonElement>, val expected: Expected)

    @Serializable
    private data class Expected(val status: String, val errors: List<ErrorCase>)

    @Test
    fun cases() {
        val cases = loadFixture<Fixture>("submission-gate.json").cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val result = SubmissionGate.check(case.version, case.files.mapValues { it.value.bytes() })
            val (status, errors) = when (result) {
                SubmissionGateResult.Ok -> "ok" to emptyList()
                SubmissionGateResult.Missing -> "missing" to emptyList()
                is SubmissionGateResult.Invalid -> "invalid" to result.errors
            }
            assertEquals(case.expected.status, status, case.name)
            assertEquals(case.expected.errors, errors.map { it.toCase() }, case.name)
        }
    }
}
