package com.abominableenterprise.cherkit.analytics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Shape of `fixtures/analytics/gate.json`. */
@Serializable
data class GateFixture(val cases: List<Case>) {
    @Serializable
    data class Case(
        val description: String,
        val buildEnabled: Boolean,
        val consentGranted: Boolean,
        val sessionActive: Boolean,
        val event: String,
        val sent: Boolean,
    )
}

/** Shape of `fixtures/analytics/lifecycle.json`. */
@Serializable
data class LifecycleFixture(val cases: List<Case>) {
    @Serializable
    data class Operation(
        val op: String,
        val value: Boolean? = null,
        val event: String? = null,
        val sent: Boolean? = null,
        val call: String? = null,
    )

    @Serializable
    data class Case(
        val description: String,
        val buildEnabled: Boolean,
        val operations: List<Operation>,
        val calls: List<String>,
    )
}

val fixtureJson = Json { ignoreUnknownKeys = true }

/** Reads `fixtures/analytics/<name>`; the root build sets `cherkit.fixtures` on every Test task. */
inline fun <reified T> analyticsFixture(name: String): T {
    val dir = requireNotNull(System.getProperty("cherkit.fixtures")) {
        "cherkit.fixtures is not set; run the tests through Gradle"
    }
    return fixtureJson.decodeFromString(File(dir, "analytics/$name").readText())
}
