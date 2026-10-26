package com.abominableenterprise.cherkit.whatsnew

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs `fixtures/whatsnew/checker.json` through a [LoadedReleaseNotesSource] and a [KeyValueWhatsNewStore] over an
 * in-memory map; Swift's `CheckerFixtureTests` runs the same cases.
 */
class CheckerFixtureTest {
    @Serializable
    private data class Fixture(val cases: List<Case>)

    @Serializable
    private data class Case(
        val name: String,
        val files: Map<String, JsonElement>,
        val store: StoreState,
        val skipped: List<String> = emptyList(),
        val steps: List<Step>,
    )

    @Serializable
    private data class StoreState(val lastSeen: String?, val history: List<String>)

    @Serializable
    private data class Step(
        val launch: String? = null,
        val locale: List<String> = emptyList(),
        val show: List<Shown> = emptyList(),
        val markSeen: Boolean = false,
        val history: List<String>? = null,
        val entries: List<HistoryEntry> = emptyList(),
        val store: StoreState,
    )

    @Serializable
    private data class Shown(val version: String, val date: String?, val bullets: List<String>)

    @Serializable
    private data class HistoryEntry(val version: String, val date: String?, val bullets: List<String>, val isInstalled: Boolean)

    @Test
    fun cases() {
        val cases = loadFixture<Fixture>("checker.json").cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val skipped = mutableListOf<String>()
            // A string is the raw file text (e.g. malformed JSON); anything else is the JSON itself.
            val files = case.files.map { (name, value) ->
                name to ((value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: value.toString())
            }
            val source = LoadedReleaseNotesSource(onSkip = { name, _ -> skipped += name }) { files }
            val storage = MapKeyValueStorage()
            val store = KeyValueWhatsNewStore(storage)
            case.store.lastSeen?.let(store::setLastSeenVersion)
            store.setInstallHistory(case.store.history)
            val checker = WhatsNewChecker(source, store)
            case.steps.forEachIndexed { index, step ->
                val label = "${case.name}, step ${index + 1}"
                when {
                    step.launch != null -> assertEquals(
                        step.show,
                        checker.onLaunch(step.launch, step.locale).map { Shown(it.version.toString(), it.date, it.bullets) },
                        label,
                    )
                    step.markSeen -> checker.markSeen()
                    step.history != null -> assertEquals(
                        step.entries,
                        checker.history(step.history).map {
                            HistoryEntry(it.version.toString(), it.date, it.bullets, it.isInstalled)
                        },
                        label,
                    )
                    else -> fail("unknown step: $label")
                }
                assertEquals(step.store, StoreState(store.lastSeenVersion(), store.installHistory()), label)
            }
            assertEquals(case.skipped.toSet(), skipped.toSet(), case.name)
        }
    }
}
