package com.abominableenterprise.cherkit.telemetry.firebase

internal class RecordingFirebase : FirebaseClient {
    val calls = mutableListOf<String>()
    val crashes = mutableListOf<Boolean>()
    val performance = mutableListOf<Boolean>()
    data class Error(val type: String, val domain: String?, val code: Int?, val attributes: Map<String, String>)
    val errors = mutableListOf<Error>()
    val logs = mutableListOf<String>()
    val keys = mutableMapOf<String, String>()
    val traces = mutableListOf<Trace>()
    val failures = mutableSetOf<String>()

    private fun call(name: String) {
        calls += name
        if (name in failures) throw AssertionError("SDK failure")
    }
    override fun crashCollection(enabled: Boolean) { call("crash"); crashes += enabled }
    override fun performanceCollection(enabled: Boolean) { call("perf"); performance += enabled }
    override fun error(type: String, domain: String?, code: Int?, attributes: Map<String, String>) {
        call("error"); errors += Error(type, domain, code, attributes)
    }
    override fun log(message: String) { call("log"); logs += message }
    override fun key(name: String, value: String) { call("key"); keys[name] = value }
    override fun newTrace(name: String): FirebaseTrace {
        call("trace")
        return Trace(name).also { traces += it }
    }
    inner class Trace(val name: String) : FirebaseTrace {
        var starts = 0
        var stops = 0
        val attributes = mutableMapOf<String, String>()
        override fun start() { call("start"); starts++ }
        override fun stop() { call("stop"); stops++ }
        override fun attribute(name: String, value: String) { call("attribute"); attributes[name] = value }
    }
}
