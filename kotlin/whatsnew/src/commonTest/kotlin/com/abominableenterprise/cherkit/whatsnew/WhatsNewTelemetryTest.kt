package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsNewTelemetryTest {
    private val text = """{"version":"1.1","notes":{"en":["New tools."],"de":["Neue Werkzeuge."]}}"""
    private val privateText = "private text https://example.com/private"

    @Test
    fun loaderReportsEachInvalidFileExactlyOnceWithoutNamesOrMessages() {
        val sink = Sink()
        val skipped = mutableListOf<String>()
        val notes = ReleaseNotesLoader.load(
            mapOf("1.1.json" to text.encodeToByteArray(), "private.json" to privateText.encodeToByteArray(),
                "1.3.json" to "{}".encodeToByteArray(), "ignored.txt" to byteArrayOf()),
            telemetrySink = sink, onSkip = { name, _ -> skipped += name },
        )
        assertEquals(1, notes.size)
        assertEquals(listOf("1.3.json", "private.json"), skipped)
        assertEquals(listOf(2, 2), sink.codes)
        assertTrue(sink.breadcrumbs.isEmpty())
    }

    @Test
    fun absentFilesThrowingLoaderAndInvalidBytesHaveDistinctCodes() {
        for (loader in listOf<() -> List<Pair<String, String>>>(
            { emptyList() }, { listOf("ignored.txt" to text) }, { error(privateText) },
        )) {
            val sink = Sink()
            val source = LoadedReleaseNotesSource(telemetrySink = sink, loader = loader)
            assertEquals(emptyList(), source.releaseNotes())
            assertEquals(listOf(1), sink.codes)
        }
        val sink = Sink()
        ReleaseNotesLoader.load(mapOf("private.json" to byteArrayOf(0xff.toByte())), telemetrySink = sink)
        assertEquals(listOf(2), sink.codes)
    }

    @Test
    fun checkerForwardsSinkWithoutDuplicatingSourceErrors() {
        for (sourceHasSink in listOf(false, true)) {
            val sink = Sink()
            val source = LoadedReleaseNotesSource(telemetrySink = sink.takeIf { sourceHasSink }) {
                listOf("private.json" to privateText)
            }
            val checker = WhatsNewChecker(source, store(), sink)
            assertEquals(emptyList(), checker.onLaunch("1.1", listOf("en")))
            checker.markSeen()
            assertEquals(listOf(2), sink.codes)
            assertTrue(sink.breadcrumbs.isEmpty())
        }
        val sourceSink = Sink()
        val checkerSink = Sink()
        WhatsNewChecker(LoadedReleaseNotesSource(telemetrySink = sourceSink) { emptyList() }, store(), checkerSink)
            .history(listOf("en"))
        assertEquals(listOf(1), sourceSink.codes)
        assertTrue(checkerSink.codes.isEmpty())
    }

    @Test
    fun localeFallbackReportsOncePerOperationNotPerRelease() {
        val cases = listOf(
            listOf("de") to false,
            listOf("DE") to false,
            listOf("de-DE") to true,
            listOf("fr", "de") to true,
            listOf("fr") to true,
            emptyList<String>() to false,
        )
        for ((locale, fallback) in cases) {
            val sink = Sink()
            val source = LoadedReleaseNotesSource {
                listOf("1.1.json" to text, "1.2.json" to text.replace("1.1", "1.2"))
            }
            val checker = WhatsNewChecker(source, store(), sink)
            assertEquals(2, checker.onLaunch("1.2", locale).size)
            assertEquals(if (fallback) listOf(3) else emptyList(), sink.codes)
            sink.codes.clear()
            checker.history(locale)
            assertEquals(if (fallback) listOf(3) else emptyList(), sink.codes)
        }
    }

    @Test
    fun localeNormalizationAndUnusedNotesDoNotReport() {
        val sink = Sink()
        val source = LoadedReleaseNotesSource {
            listOf("1.1.json" to text.replace("\"de\"", "\"pt-BR\""))
        }
        val checker = WhatsNewChecker(source, store(), sink)
        checker.onLaunch("1.1", listOf(" PT_br "))
        assertTrue(sink.codes.isEmpty())
        val firstLaunch = WhatsNewChecker(source, store(lastSeen = null), sink)
        assertEquals(emptyList(), firstLaunch.onLaunch("1.1", listOf(privateText)))
        firstLaunch.markSeen()
        assertTrue(sink.codes.isEmpty())
        assertTrue(sink.breadcrumbs.isEmpty())
    }

    @Test
    fun shownIsOnlyReportedWhenPendingNotesAreMarkedSeenAndHasNoAttributes() {
        val sink = Sink()
        val checker = WhatsNewChecker(LoadedReleaseNotesSource { listOf("1.1.json" to text) }, store(), sink)
        checker.markSeen()
        checker.onLaunch("1.1", listOf("en"))
        assertTrue(sink.breadcrumbs.isEmpty())
        checker.markSeen()
        checker.markSeen()
        checker.onLaunch("1.1", listOf("en"))
        checker.markSeen()
        assertEquals(listOf("cherkit.whatsnew.shown"), sink.breadcrumbs)
        assertTrue(sink.codes.isEmpty())
    }

    @Test
    fun nullAndThrowingSinksDoNotChangeResults() {
        for (sink in listOf(null, object : TelemetrySink {
            override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) { error(privateText) }
            override fun breadcrumb(name: String, attributes: Map<String, Any>) { error(privateText) }
        })) {
            val checker = WhatsNewChecker(LoadedReleaseNotesSource(telemetrySink = sink) {
                listOf("1.1.json" to text, "private.json" to privateText)
            }, store(), sink)
            assertEquals(1, checker.onLaunch("1.1", listOf(privateText)).size)
            checker.markSeen()
            assertEquals(emptyList(), checker.onLaunch("1.1", listOf("en")))
            assertEquals(emptyList(), LoadedReleaseNotesSource(telemetrySink = sink) { error(privateText) }.releaseNotes())
        }
    }

    @Test
    fun customFilesSourceReportsDecodeFailuresRatherThanMissing() {
        for (includeValid in listOf(false, true)) {
            val sink = Sink()
            val source = object : ReleaseNotesSource {
                override fun notesFiles(): Map<String, ByteArray> = buildMap {
                    put("private.json", privateText.encodeToByteArray())
                    if (includeValid) put("1.1.json", text.encodeToByteArray())
                }
            }
            val notes = WhatsNewChecker(source, store(), sink).history(listOf("en"))
            assertEquals(if (includeValid) 1 else 0, notes.size)
            assertEquals(listOf(2), sink.codes)
        }
    }

    @Test
    fun positionalLoaderCallbackStillCompiles() {
        val skips = mutableListOf<String>()
        val skip: SkipHandler = { name, _ -> skips += name }
        ReleaseNotesLoader.load(mapOf("bad.json" to byteArrayOf()), skip)
        assertEquals(listOf("bad.json"), skips)
    }

    @Test
    fun throwingCustomSourceReportsMissingExactlyOnce() {
        val sink = Sink()
        val source = object : ReleaseNotesSource {
            override fun notesFiles(): Map<String, ByteArray> = error(privateText)
        }
        assertEquals(emptyList(), WhatsNewChecker(source, store(), sink).history(listOf("en")))
        assertEquals(listOf(1), sink.codes)
    }

    @Test
    fun throwingSkipHandlerDoesNotCauseASecondMissingError() {
        val sink = Sink()
        val source = LoadedReleaseNotesSource(onSkip = { _, _ -> error(privateText) }) {
            listOf("private.json" to privateText, "1.1.json" to text)
        }
        assertEquals(1, WhatsNewChecker(source, store(), sink).history(listOf("en")).size)
        assertEquals(listOf(2), sink.codes)
    }

    private fun store(lastSeen: String? = "1.0"): WhatsNewStore =
        KeyValueWhatsNewStore(MapKeyValueStorage()).also { if (lastSeen != null) it.setLastSeenVersion(lastSeen) }

    private class Sink : TelemetrySink {
        val codes = mutableListOf<Int>()
        val breadcrumbs = mutableListOf<String>()
        override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) {
            assertEquals("cherkit.whatsnew", domain)
            assertTrue(attributes.isEmpty())
            codes += code
        }
        override fun breadcrumb(name: String, attributes: Map<String, Any>) {
            assertTrue(attributes.isEmpty())
            breadcrumbs += name
        }
    }
}
