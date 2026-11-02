package com.abominableenterprise.cherkit.telemetry

internal class RecordingBackend : TelemetryBackend {
    val calls = mutableListOf<String>()
    val errors = mutableListOf<TelemetryError>()
    val attributes = mutableListOf<Map<String, Any>>()
    val names = mutableListOf<String>()
    val keys = mutableListOf<Pair<String, Any>>()
    val traceAttributes = mutableListOf<Triple<String, String, Any>>()
    val endings = mutableListOf<Pair<String, SpanStatus>>()
    val liveTraces = mutableSetOf<String>()
    val failNext = mutableSetOf<String>()
    var failure: Throwable = IllegalStateException("backend failure")
    private var nextID = 0

    private fun call(name: String) {
        calls += name
        if (failNext.remove(name)) throw failure
    }

    override fun start() = call("start")
    override fun stop() = call("stop")
    override fun recordError(error: TelemetryError, attributes: Map<String, Any>) {
        call("error")
        errors += error
        this.attributes += attributes
    }
    override fun breadcrumb(name: String, attributes: Map<String, Any>) {
        call("breadcrumb")
        names += name
        this.attributes += attributes
    }
    override fun startTrace(name: String, attributes: Map<String, Any>): String {
        call("startTrace")
        names += name
        this.attributes += attributes
        return "trace-${++nextID}".also { liveTraces += it }
    }
    override fun stopTrace(id: String, status: SpanStatus) {
        liveTraces -= id
        call("stopTrace")
        endings += id to status
    }
    override fun setAttribute(traceID: String, key: String, value: Any) {
        call("setAttribute")
        traceAttributes += Triple(traceID, key, value)
    }
    override fun setKey(key: String, value: Any) {
        call("key")
        keys += key to value
    }
}
