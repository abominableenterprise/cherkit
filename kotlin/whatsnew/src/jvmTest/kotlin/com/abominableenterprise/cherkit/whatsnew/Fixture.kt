package com.abominableenterprise.cherkit.whatsnew

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

/** Reads `fixtures/whatsnew/<name>`; the root build sets `cherkit.fixtures` on every Test task. */
internal inline fun <reified T> loadFixture(name: String): T {
    val dir = requireNotNull(System.getProperty("cherkit.fixtures")) { "cherkit.fixtures is not set; run the tests through Gradle" }
    return fixtureJson.decodeFromString(File(dir, "whatsnew/$name").readText())
}

internal val fixtureJson = Json { ignoreUnknownKeys = true }

/** An expected error in a fixture. */
@Serializable
internal data class ErrorCase(val code: String, val message: String)

internal fun NotesFileError.toCase(): ErrorCase = ErrorCase(code.rawValue, message)

/** Fixture JSON as file bytes, as the app would bundle it. */
internal fun JsonElement.bytes(): ByteArray = toString().encodeToByteArray()
