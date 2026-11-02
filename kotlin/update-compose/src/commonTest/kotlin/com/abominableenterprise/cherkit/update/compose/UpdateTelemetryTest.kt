package com.abominableenterprise.cherkit.update.compose

import com.abominableenterprise.cherkit.core.TelemetrySink
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateTelemetryTest {
    private class Sink : TelemetrySink {
        val errors = mutableListOf<Pair<String, Int>>()
        val crumbs = mutableListOf<String>()
        override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) {
            assertEquals(emptyMap(), attributes)
            errors += domain to code
        }
        override fun breadcrumb(name: String, attributes: Map<String, Any>) {
            assertEquals(emptyMap(), attributes)
            crumbs += name
        }
    }

    @Test
    fun positionalGateCallbackStillCompiles() = runTest {
        val allowed: () -> Boolean = { false }
        assertEquals(UpdateLaunchResult.SUPPRESSED,
            UpdateLauncher.launch(FakeUpdateFlow(), InMemoryUpdatePromptStore(), UpdateSession(), allowed))
    }

    @Test
    fun failuresReportExactlyOnceWithoutMessages() = runTest {
        for (requestFailure in listOf(true, false)) {
            for (throws in listOf(true, false)) {
                val sink = Sink()
                val flow = FakeUpdateFlow()
                if (requestFailure) flow.requestError = IllegalStateException("private URL and version")
                else if (throws) flow.startError = IllegalStateException("private message")
                else flow.startResult = false
                UpdateLauncher.launch(flow, InMemoryUpdatePromptStore(), UpdateSession(), sink)
                assertEquals(listOf("cherkit.update" to if (requestFailure) 1 else 2), sink.errors)
            }
        }
    }

    @Test
    fun availableAndDownloadedUseOnlyStableNames() = runTest {
        val sink = Sink()
        val flow = FakeUpdateFlow()
        val session = UpdateSession()
        val store = InMemoryUpdatePromptStore()
        UpdateLauncher.launch(flow, store, session, sink)
        flow.status = FakeUpdateFlow.downloaded(42)
        repeat(2) { UpdateLauncher.launch(flow, store, session, sink) }
        assertEquals(listOf("cherkit.update.available", "cherkit.update.downloaded"), sink.crumbs)
        assertEquals(emptyList(), sink.errors)
    }

    @Test
    fun nullOrBrokenSinkDoesNotChangeFlowAndCancellationIsNotAnError() = runTest {
        val broken = object : TelemetrySink {
            override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) = error("sink")
            override fun breadcrumb(name: String, attributes: Map<String, Any>) = error("sink")
        }
        for (sink in listOf(null, broken)) {
            val flow = FakeUpdateFlow(requestError = IllegalStateException("private"))
            assertEquals(UpdateLaunchResult.CHECK_FAILED,
                UpdateLauncher.launch(flow, InMemoryUpdatePromptStore(), UpdateSession(), sink))
            flow.requestError = null
            assertEquals(UpdateLaunchResult.STARTED,
                UpdateLauncher.launch(flow, InMemoryUpdatePromptStore(), UpdateSession(), sink))
        }
        val sink = Sink()
        assertFailsWith<CancellationException> {
            UpdateLauncher.launch(FakeUpdateFlow(requestError = CancellationException()),
                InMemoryUpdatePromptStore(), UpdateSession(), sink)
        }
        assertEquals(emptyList(), sink.errors)
    }
}
