package com.abominableenterprise.cherkit.whatsnew

import kotlin.test.Test
import kotlin.test.assertEquals

class LoadedReleaseNotesSourceCompatibilityTest {
    @Test
    fun positionalCallbacksForwardToTheLoaderAndSkipHandler() {
        val skipped = mutableListOf<String>()
        val onSkip: SkipHandler = { name, _ -> skipped += name }
        var loads = 0
        val loader = {
            loads++
            listOf("broken.json" to "not JSON")
        }
        val source = LoadedReleaseNotesSource(onSkip, loader)

        assertEquals(emptyList(), source.releaseNotes())
        assertEquals(1, loads)
        assertEquals(listOf("broken.json"), skipped)
    }

    @Test
    fun namedAndTrailingCallbacksStillUseTheCurrentApi() {
        val onSkip: SkipHandler? = null
        val loader: () -> List<Pair<String, String>> = { emptyList() }
        val sources = listOf(
            LoadedReleaseNotesSource(onSkip = onSkip, loader = loader),
            LoadedReleaseNotesSource(onSkip = onSkip, telemetrySink = null, loader = loader),
            LoadedReleaseNotesSource(onSkip) { loader() },
            LoadedReleaseNotesSource { loader() },
        )
        sources.forEach { assertEquals(emptyList(), it.releaseNotes()) }
    }
}
