package com.abominableenterprise.cherkit.telemetry.firebase

import com.abominableenterprise.cherkit.telemetry.SpanStatus
import com.abominableenterprise.cherkit.telemetry.TelemetryBackend
import com.abominableenterprise.cherkit.telemetry.TelemetryError

internal class FirebaseBackend(
    private val client: FirebaseClient,
    private val performanceEnabled: Boolean = false,
) : TelemetryBackend {
    private var active = false
    private var restricted = false
    private var nextID = 0L
    private class Trace(val sdk: FirebaseTrace, val keys: MutableSet<String> = mutableSetOf())
    private val traces = mutableMapOf<String, Trace>()
    private val keys = mutableSetOf<String>()

    // Firebase auto-initializes independently of the facade: close both collection gates now.
    init { stop() }

    override fun start() {
        active = bestEffort { client.crashCollection(true) }
        bestEffort { client.performanceCollection(active && !restricted && performanceEnabled) }
        if (!active) stop()
    }

    override fun stop() {
        active = false
        bestEffort { client.performanceCollection(false) }
        bestEffort { client.crashCollection(false) }
        closeTraces()
        clearKeys()
    }

    override fun updateRestricted(restricted: Boolean) {
        this.restricted = restricted
        if (restricted) {
            bestEffort { client.performanceCollection(false) }
            closeTraces()
            clearKeys()
        } else if (active) {
            bestEffort { client.performanceCollection(performanceEnabled) }
        }
    }

    override fun recordError(error: TelemetryError, attributes: Map<String, Any>) {
        if (!active) return
        bestEffort {
            client.error(FirebaseValues.type(error.type), FirebaseValues.domain(error.domain), error.code,
                FirebaseValues.attributes(attributes).filterKeys { !it.startsWith("cherkit.error.") }
                    .entries.take(60).associate { it.toPair() })
        }
    }

    override fun breadcrumb(name: String, attributes: Map<String, Any>) {
        if (!active || restricted || !FirebaseValues.name(name)) return
        val suffix = FirebaseValues.attributes(attributes).entries.joinToString(" ") { "${it.key}=${it.value}" }
        bestEffort { client.log(if (suffix.isEmpty()) name else "$name $suffix") }
    }

    override fun setKey(key: String, value: Any) {
        if (!active || restricted || !FirebaseValues.name(key)) return
        val safe = FirebaseValues.value(value) ?: return
        if (key !in keys && keys.size >= 60) return
        keys += key
        bestEffort { client.key(key, safe) }
    }

    override fun startTrace(name: String, attributes: Map<String, Any>): String {
        if (!active || restricted || !performanceEnabled || !FirebaseValues.name(name)) return ""
        val trace = try { Trace(client.newTrace(name)) } catch (_: Throwable) { return "" }
        val id = "trace-${++nextID}"
        traces[id] = trace
        attributes.forEach { (key, value) -> setAttribute(id, key, value) }
        if (!bestEffort { trace.sdk.start() }) {
            traces.remove(id)
            bestEffort { trace.sdk.stop() }
            return ""
        }
        return id
    }

    override fun setAttribute(traceID: String, key: String, value: Any) {
        if (!active || restricted) return
        val trace = traces[traceID] ?: return
        if (!FirebaseValues.traceKey(key)) return
        val safe = FirebaseValues.value(value)?.takeIf { it.length <= 100 } ?: return
        if (key !in trace.keys && trace.keys.size >= 5) return
        trace.keys += key
        bestEffort { trace.sdk.attribute(key, safe) }
    }

    override fun stopTrace(id: String, status: SpanStatus) {
        val trace = traces.remove(id) ?: return
        bestEffort { trace.sdk.stop() }
    }

    private fun closeTraces() {
        val live = traces.values.toList()
        traces.clear()
        live.forEach { bestEffort { it.sdk.stop() } }
    }

    private fun clearKeys() {
        val old = keys.toList()
        keys.clear()
        // Crashlytics has no removeCustomKey API. Neutralize values, not user identifiers.
        old.forEach { bestEffort { client.key(it, "") } }
    }
}
