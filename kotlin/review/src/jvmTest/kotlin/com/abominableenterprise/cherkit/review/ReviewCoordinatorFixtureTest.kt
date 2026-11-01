package com.abominableenterprise.cherkit.review

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Runs every case in `fixtures/review/coordinator.json`; Swift's `ReviewCoordinatorFixtureTests` runs the same. */
class ReviewCoordinatorFixtureTest {
    @Serializable
    private data class Fixture(val cases: List<Case>)

    @Serializable
    private data class Case(
        val name: String,
        val version: String,
        val enabled: Boolean? = null,
        val successThreshold: Int? = null,
        val cooldownSeconds: Double? = null,
        val stored: Stored? = null,
        val steps: List<Step>,
    )

    @Serializable
    private data class Stored(val successCount: Int, val isDue: Boolean, val lastPromptedVersion: String? = null)

    @Serializable
    private data class Step(
        val `do`: String,
        val times: Int? = null,
        val source: String? = null,
        val visible: Boolean? = null,
        val value: Boolean? = null,
        val seconds: Double? = null,
        val version: String? = null,
        val isIdle: Boolean? = null,
        val shouldPrompt: Boolean? = null,
        val isDue: Boolean? = null,
        val cooldownRemaining: Double? = null,
        val result: Boolean? = null,
        val successCount: Int? = null,
        val lastPromptedVersion: String? = null,
    )

    @Test
    fun everyFixtureCase() {
        val dir = requireNotNull(System.getProperty("cherkit.fixtures")) { "cherkit.fixtures is not set" }
        val fixture = Json { ignoreUnknownKeys = true }
            .decodeFromString<Fixture>(File(dir, "review/coordinator.json").readText())
        assertTrue(fixture.cases.size >= 30)
        fixture.cases.forEach(::run)
    }

    private fun run(case: Case) {
        val store = InMemoryReviewStore()
        case.stored?.let { store.state = ReviewState(it.successCount, it.isDue, it.lastPromptedVersion) }
        var clockMillis = 0L
        var eligible = true
        fun make(version: String) = ReviewCoordinator(
            store = store,
            appVersion = version,
            enabled = case.enabled ?: true,
            successThreshold = case.successThreshold ?: ReviewCoordinator.DEFAULT_SUCCESS_THRESHOLD,
            cooldown = case.cooldownSeconds?.seconds ?: ReviewCoordinator.DEFAULT_COOLDOWN,
            isEligible = { eligible },
            clock = { clockMillis },
        )
        var coordinator = make(case.version)
        case.steps.forEachIndexed { index, step ->
            val at = "${case.name} / step $index ${step.`do`}"
            when (step.`do`) {
                "recordSuccess" -> repeat(step.times ?: 1) { coordinator.recordSuccess() }
                "arm" -> coordinator.arm()
                "noteInterruption" -> coordinator.noteInterruption()
                "interruption" -> coordinator.setInterruptionVisible(step.visible!!, step.source!!)
                "eligible" -> eligible = step.value!!
                "advance" -> clockMillis += (step.seconds!! * 1000).toLong()
                "relaunch" -> coordinator = make(step.version ?: coordinator.appVersion)
                "check" -> {
                    step.shouldPrompt?.let { assertEquals(it, coordinator.shouldPrompt(step.isIdle ?: true), at) }
                    step.isDue?.let { assertEquals(it, coordinator.isDue, at) }
                    step.cooldownRemaining?.let {
                        assertEquals((it * 1000).toLong().milliseconds, coordinator.cooldownRemaining, at)
                    }
                }
                "markPrompted" -> assertEquals(step.result, coordinator.markPrompted(), at)
                "expectStored" -> assertEquals(
                    ReviewState(step.successCount!!, step.isDue!!, step.lastPromptedVersion),
                    store.state,
                    at,
                )
                else -> fail("unknown step $at")
            }
        }
    }
}
