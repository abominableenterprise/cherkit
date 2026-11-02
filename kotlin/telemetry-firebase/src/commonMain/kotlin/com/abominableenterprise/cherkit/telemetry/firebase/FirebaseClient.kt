package com.abominableenterprise.cherkit.telemetry.firebase

internal interface FirebaseClient {
    fun crashCollection(enabled: Boolean)
    fun performanceCollection(enabled: Boolean)
    fun error(type: String, domain: String?, code: Int?, attributes: Map<String, String>)
    fun log(message: String)
    fun key(name: String, value: String)
    fun newTrace(name: String): FirebaseTrace
}

internal interface FirebaseTrace {
    fun start()
    fun stop()
    fun attribute(name: String, value: String)
}

internal inline fun bestEffort(call: () -> Unit): Boolean = try {
    call()
    true
} catch (_: Throwable) {
    false
}
