package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.TelemetrySink
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsNewTelemetryFixtureTest {
    @Serializable
    private data class Fixture(
        val domain: String,
        val breadcrumb: String,
        val fileCases: List<FileCase>,
        val localeCases: List<LocaleCase>,
        val fallbackCode: Int,
    )
    @Serializable
    private data class FileCase(val result: String, val code: Int)
    @Serializable
    private data class LocaleCase(val preferred: List<String>, val fallback: Boolean)

    @Test
    fun sharedTelemetryContract() {
        val fixture = loadFixture<Fixture>("kotlin-telemetry.json")
        val errors = mutableListOf<Pair<String, Int>>()
        val breadcrumbs = mutableListOf<String>()
        val sink = object : TelemetrySink {
            override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) {
                assertTrue(attributes.isEmpty())
                errors += domain to code
            }
            override fun breadcrumb(name: String, attributes: Map<String, Any>) {
                assertTrue(attributes.isEmpty())
                breadcrumbs += name
            }
        }
        for (case in fixture.fileCases) {
            errors.clear()
            val source = LoadedReleaseNotesSource(telemetrySink = sink) {
                when (case.result) {
                    "missing" -> emptyList()
                    "throws" -> error("private path")
                    "undecodable" -> listOf("private.json" to "private content")
                    else -> error("unknown fixture result")
                }
            }
            WhatsNewChecker(source, store(), sink).history(listOf("en"))
            assertEquals(listOf(fixture.domain to case.code), errors)
        }
        for (case in fixture.localeCases) {
            errors.clear()
            breadcrumbs.clear()
            val source = LoadedReleaseNotesSource {
                listOf("1.1.json" to """{"version":"1.1","notes":{"en":["New tools."],"de":["Neue Werkzeuge."]}}""")
            }
            val checker = WhatsNewChecker(source, store(), sink)
            assertEquals(1, checker.onLaunch("1.1", case.preferred).size)
            assertEquals(if (case.fallback) listOf(fixture.domain to fixture.fallbackCode) else emptyList(), errors)
            checker.markSeen()
            checker.markSeen()
            assertEquals(listOf(fixture.breadcrumb), breadcrumbs)
        }
    }

    private fun store(): WhatsNewStore = KeyValueWhatsNewStore(MapKeyValueStorage()).also { it.setLastSeenVersion("1.0") }
}
