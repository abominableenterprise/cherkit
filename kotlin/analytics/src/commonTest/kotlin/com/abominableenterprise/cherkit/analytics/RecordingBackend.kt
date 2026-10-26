package com.abominableenterprise.cherkit.analytics

/**
 * In-memory backend that records every call as "start", "stop" or "capture:<name>".
 * [failNext] makes the next call of that kind throw after it is recorded.
 */
class RecordingBackend : AnalyticsBackend {
    val calls = mutableListOf<String>()
    val failNext = mutableSetOf<String>()

    override fun start() = record("start", "start")
    override fun capture(name: String) = record("capture:$name", "capture")
    override fun stop() = record("stop", "stop")

    private fun record(call: String, kind: String) {
        calls += call
        if (failNext.remove(kind)) throw IllegalStateException("injected $kind failure")
    }
}

/** An event with any name, so fixtures can also feed names an app's enum would never contain. */
data class NamedEvent(override val eventName: String) : AnalyticsEvent
