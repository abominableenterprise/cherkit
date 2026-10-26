package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs `fixtures/whatsnew/store-text.json`; Swift's `StoreTextFixtureTests` runs the same cases. */
class StoreTextFixtureTest {
    @Serializable
    private data class Fixture(val cases: List<Case>)

    @Serializable
    private data class Case(val name: String, val notes: Notes, val locale: String, val limit: Int? = null, val expected: Expected)

    @Serializable
    private data class Notes(val show: Boolean, val notes: Map<String, List<String>>)

    @Serializable
    private data class Expected(val text: String? = null, val length: Int? = null, val tooLong: Int? = null, val noNotes: Boolean? = null)

    @Test
    fun cases() {
        val cases = loadFixture<Fixture>("store-text.json").cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val notes = ReleaseNotes(AppVersion.parse("1.0")!!, show = case.notes.show, notes = case.notes.notes)
            val result = case.limit?.let { StoreText.make(notes, case.locale, it) } ?: StoreText.make(notes, case.locale)
            val expected = case.expected
            when (result) {
                is StoreTextResult.Text -> {
                    assertNull(expected.tooLong, case.name)
                    assertNull(expected.noNotes, case.name)
                    expected.text?.let { assertEquals(it, result.text, case.name) }
                    expected.length?.let { assertEquals(it, result.text.length, case.name) }
                }
                is StoreTextResult.TooLong -> assertEquals(expected.tooLong, result.length, case.name)
                StoreTextResult.NoNotes -> assertEquals(true, expected.noNotes, case.name)
            }
        }
    }
}
