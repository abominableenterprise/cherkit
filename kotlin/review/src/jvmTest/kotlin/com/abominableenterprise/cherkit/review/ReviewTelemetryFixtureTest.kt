package com.abominableenterprise.cherkit.review

import com.abominableenterprise.cherkit.core.TelemetrySink
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReviewTelemetryFixtureTest {
    @Serializable
    private data class Fixture(val breadcrumb: String, val cases: List<Case>)
    @Serializable
    private data class Case(
        val name: String,
        val due: Boolean,
        val enabled: Boolean,
        val eligible: Boolean,
        val prompted: Boolean,
        val breadcrumbs: Int,
    )

    @Test
    fun fixtureDefinesPromptBreadcrumbContract() {
        val fixture = Json.decodeFromString<Fixture>(File(
            System.getProperty("cherkit.fixtures"), "review/kotlin-telemetry.json",
        ).readText())
        for (case in fixture.cases) {
            val crumbs = mutableListOf<String>()
            val sink = object : TelemetrySink {
                override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) = error("unexpected error")
                override fun breadcrumb(name: String, attributes: Map<String, Any>) {
                    assertEquals(emptyMap(), attributes)
                    crumbs += name
                }
            }
            val review = ReviewCoordinator(
                InMemoryReviewStore(ReviewState(isDue = case.due)), "private-version",
                enabled = case.enabled, isEligible = { case.eligible }, telemetrySink = sink,
            )
            assertEquals(case.prompted, review.markPrompted(), case.name)
            assertFalse(review.markPrompted(), case.name)
            assertEquals(List(case.breadcrumbs) { fixture.breadcrumb }, crumbs, case.name)
        }
    }
}
