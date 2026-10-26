package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs `fixtures/whatsnew/notes-file.json`; Swift's `NotesFileFixtureTests` runs the same cases. */
class NotesFileFixtureTest {
    @Serializable
    private data class Fixture(val cases: List<Case>)

    @Serializable
    private data class Case(
        val name: String,
        val fileName: String,
        val file: JsonElement? = null,
        val text: String? = null,
        val errors: List<ErrorCase>,
        val parsed: Parsed? = null,
    )

    @Serializable
    private data class Parsed(val version: String, val date: String?, val show: Boolean, val notes: Map<String, List<String>>)

    @Test
    fun cases() {
        val cases = loadFixture<Fixture>("notes-file.json").cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val data = case.text?.encodeToByteArray() ?: case.file?.bytes() ?: ByteArray(0)
            val result = NotesFile.read(data, case.fileName)
            assertEquals(case.errors, result.errors.map { it.toCase() }, case.name)
            assertEquals(case.errors.isEmpty(), result.isValid, case.name)
            case.parsed?.let { parsed ->
                val expected = ReleaseNotes(AppVersion.parse(parsed.version)!!, parsed.date, parsed.show, parsed.notes)
                assertEquals(expected, result.notes, case.name)
            }
        }
    }

    @Test
    fun errorCodesMatchSwift() {
        assertEquals("storeTextTooLong", NotesFileErrorCode.STORE_TEXT_TOO_LONG.rawValue)
        assertEquals(10, NotesFileErrorCode.entries.size)
    }

    @Test
    fun notesFileVersionNeedsDigitsAndDots() {
        assertEquals(AppVersion.parse("1.4.0"), NotesFileRules.notesFileVersion("whatsnew/1.4.json"))
        assertNull(NotesFileRules.notesFileVersion("v1.4.json"))
        assertNull(NotesFileRules.notesFileVersion("1.4 (2).json"))
        assertNull(NotesFileRules.notesFileVersion("1.4.0.md"))
    }

    @Test
    fun invalidUtf8IsNotAJsonObject() {
        val result = NotesFile.read(byteArrayOf(0x7B, 0xFF.toByte(), 0x7D), "1.0.json")
        assertEquals(listOf("notJSONObject"), result.errors.map { it.code.rawValue })
    }
}
