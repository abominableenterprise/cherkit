package com.abominableenterprise.cherkit.telemetry

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class GateCase(
    val buildEnabled: Boolean,
    val enabled: Boolean,
    val restricted: Boolean,
    val errors: Int,
    val diagnostics: Int,
)

@Serializable
internal data class ValidationCase(
    val name: String,
    val validName: Boolean,
    val kind: String,
    val value: String,
    val validValue: Boolean,
)

@Serializable
internal data class LifecycleCase(
    val name: String,
    val steps: List<String>,
    val expected: List<String>,
)

internal val fixtureJson = Json { ignoreUnknownKeys = true }

internal inline fun <reified T> telemetryFixture(name: String): T {
    val dir = requireNotNull(System.getProperty("cherkit.fixtures")) {
        "cherkit.fixtures is not set; run the tests through Gradle"
    }
    return fixtureJson.decodeFromString(File(dir, "telemetry/$name").readText())
}
