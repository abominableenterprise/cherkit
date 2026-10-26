package com.abominableenterprise.cherkit.sunset

import kotlinx.serialization.json.Json
import java.io.File

/** Reads `fixtures/sunset/<name>`; the root build sets `cherkit.fixtures` on every Test task. */
internal object SunsetFixtures {
    val json: Json = Json { ignoreUnknownKeys = true }

    fun text(name: String): String {
        val dir = requireNotNull(System.getProperty("cherkit.fixtures")) {
            "cherkit.fixtures is not set; run the tests through Gradle"
        }
        return File(dir, "sunset/$name").readText()
    }
}
