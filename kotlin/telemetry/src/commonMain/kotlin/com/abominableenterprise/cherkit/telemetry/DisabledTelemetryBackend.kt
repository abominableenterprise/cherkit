package com.abominableenterprise.cherkit.telemetry

internal object DisabledTelemetryBackend : TelemetryBackend {
    override fun start() {}
    override fun stop() {}
    override fun recordError(error: TelemetryError, attributes: Map<String, Any>) {}
    override fun breadcrumb(name: String, attributes: Map<String, Any>) {}
    override fun startTrace(name: String, attributes: Map<String, Any>): String = ""
    override fun stopTrace(id: String, status: SpanStatus) {}
    override fun setAttribute(traceID: String, key: String, value: Any) {}
    override fun setKey(key: String, value: Any) {}
}
